#!/usr/bin/env python3
"""g1_trusted_verify.py — Trusted execution entry point (use this to verify G1b approval)

Do not run the validator from the current checkout.
If the validator is modified to disable checks, it cannot report its own modification
(an inherent limit of self-inspection).

This runner:
  1. Finds commit A, the last commit to modify tests/approvals/g1.yaml, from git.
  2. Verifies A's signature (signed-commit / signed-tag).
  3. Confirms A is a descendant of target commit C and that C..A changes only the approval record.
  4. **Does not obtain the validator from A**.
     If G1_VALIDATOR_COMMIT (a full 40-character SHA externally pinned by CI as a trust anchor)
     is set, obtains it there; otherwise obtains it from C.
  5. Runs the extracted validator with `python -I` (isolated mode), passing the target repository
     through G1_REPO_ROOT.
  6. Never adds tools/ from the current checkout to sys.path.

This prevents the approver from defining the checker and a modified validator from checking itself.

The runner cannot protect itself from modification.
**CI must use an external wrapper, such as tools/g1_ci_verify.sh, that extracts and runs the runner
from a pinned SHA.** In that case, pass the target repository through G1_REPO_ROOT.

  Environment variables:
    G1_REPO_ROOT         Target repository (derived from this script's location if omitted)
    G1_VALIDATOR_COMMIT  Validator source (full 40-character SHA only)
    G1_RUNNER_COMMIT     Runner source SHA (recorded only in the audit report)

  Usage:  python3 tools/g1_trusted_verify.py [--offline]
  Exit codes: 0 = no blocking violations / 1 = violations found / 2 = verification preconditions failed
"""
import os,sys
# ★ If not isolated, restart this process with `python -I`.
#   -I disables PYTHONPATH and user site-packages.
#   Removing sys.path[0] (the script location = tools/) alone is insufficient:
#   with `PYTHONPATH=. python tools/g1_trusted_verify.py`, an unsigned yaml.py in the
#   repository root could run before signature verification.
#   This restart occurs before importing any third-party package.
if not sys.flags.isolated:
    os.execv(sys.executable,[sys.executable,'-I',os.path.abspath(__file__)]+sys.argv[1:])
# Even with -I, sys.path[0] contains the script location, so remove it as well.
if sys.path and os.path.basename(sys.path[0] or '')=='tools':
    del sys.path[0]
import subprocess,tempfile,shutil,json

ROOT=os.environ.get('G1_REPO_ROOT') or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT=os.path.abspath(ROOT)
APPROVAL_REL='tests/approvals/g1.yaml'
EXTRACT=('tools/g1_validate.py','tools/g1_extract.py')

def git(*a,binary=False):
    return subprocess.run(['git','-C',ROOT]+list(a),capture_output=True,text=not binary)

def is_full_sha(v):
    v=str(v)
    return len(v)==40 and all(c in '0123456789abcdef' for c in v)

def die(msg,code=2):
    print(f"[trusted-verify] {msg}",file=sys.stderr); sys.exit(code)

def main():
    if git('rev-parse','--git-dir').returncode!=0:
        die(f"Target {ROOT} is not a git repository; set G1_REPO_ROOT")
    r=git('rev-parse','--verify','--quiet','HEAD')
    if r.returncode!=0: die("git HEAD is missing; cannot pin the approval target")
    print(f"[trusted-verify] Target repository: {ROOT}")

    lg=git('log','-1','--format=%H','--',APPROVAL_REL)
    sig=lg.stdout.strip() if lg.returncode==0 else ''
    if not sig:
        die(f"{APPROVAL_REL} is not committed; G1b approval has not been completed")

    # Read the signature type from the approval record, but do not trust the record yet;
    # it only selects the signature-verification entry point.
    import yaml   # sys.path was sanitized above
    blob=git('show',f'{sig}:{APPROVAL_REL}',binary=True)
    if blob.returncode!=0: die("Cannot read the approval record from the signed commit")
    peek=yaml.safe_load(blob.stdout.decode('utf-8')) or {}
    ev=peek.get('evidence') or {}
    kind=ev.get('kind')

    if kind=='signed-tag':
        tag=ev.get('tag')
        if not tag: die("evidence.kind=signed-tag requires evidence.tag")
        if git('verify-tag',str(tag)).returncode!=0: die(f"Signature verification failed for tag {tag}")
        rt=git('rev-list','-n','1',str(tag))
        if rt.returncode!=0 or rt.stdout.strip()!=sig:
            die(f"Tag {tag} does not point to commit {sig[:12]} containing the approval record")
    elif kind=='signed-commit':
        if git('verify-commit',sig).returncode!=0:
            die(f"Signature verification failed for approval commit {sig[:12]}")
    else:
        die("evidence.kind must be signed-commit or signed-tag")

    print(f"[trusted-verify] Signed approval commit: {sig[:12]} ({kind})")

    # ------------------------------------------------------------------
    # ★ Never obtain the validator from approval commit A.
    #   A's signer could weaken the validator together with the approval record.
    #   Source priority:
    #     1) G1_VALIDATOR_COMMIT (trust anchor externally pinned by CI)
    #     2) Target commit C (the artifact actually read by reviewers)
    #   Also restrict C..A changes to the approval record and require A to descend from C.
    # ------------------------------------------------------------------
    C=str(peek.get('target_commit') or '')
    if not is_full_sha(C):
        die("target_commit must be a full 40-character SHA-1")
    if git('rev-parse','--verify','--quiet',C+'^{commit}').stdout.strip()!=C:
        die(f"target_commit {C[:12]} cannot be resolved as an exact commit")
    if git('merge-base','--is-ancestor',C,sig).returncode!=0:
        die(f"Approval commit {sig[:12]} is not a descendant of target commit {C[:12]}")

    changed=sorted(x for x in git('diff','--name-only',f'{C}..{sig}').stdout.split() if x)
    if changed!=[APPROVAL_REL]:
        die(f"C..A changes are not limited to the approval record: {changed[:6]}\n"
            f"          The approval commit must contain only the addition of {APPROVAL_REL}")

    env_anchor=os.environ.get('G1_VALIDATOR_COMMIT')
    if env_anchor:
        # ★ Never use a mutable ref such as HEAD / main as a trust anchor.
        #   Require a full 40-character SHA and verify an exact match with rev-parse output.
        if not is_full_sha(env_anchor):
            die("G1_VALIDATOR_COMMIT must be a full 40-character SHA-1"
                f" (mutable refs are not allowed; given: {env_anchor!r})")
        r=git('rev-parse','--verify','--quiet',env_anchor+'^{commit}')
        if r.returncode!=0 or r.stdout.strip()!=env_anchor:
            die(f"G1_VALIDATOR_COMMIT {env_anchor[:12]} cannot be resolved as an exact commit")
        anchor=env_anchor; anchor_src='G1_VALIDATOR_COMMIT (externally pinned)'
    else:
        anchor=C; anchor_src='target_commit (pin with G1_VALIDATOR_COMMIT in CI)'
    print(f"[trusted-verify] Validator source: {anchor[:12]} / {anchor_src}")

    # Extract the validator from A's tree and run it in isolation.
    tmp=tempfile.mkdtemp(prefix='g1-trusted-')
    try:
        os.makedirs(os.path.join(tmp,'tools'),exist_ok=True)
        for rel in EXTRACT:
            b=git('show',f'{anchor}:{rel}',binary=True)
            if b.returncode!=0: die(f"{rel} does not exist at {anchor[:12]}")
            with open(os.path.join(tmp,rel),'wb') as f: f.write(b.stdout)
        env=dict(os.environ)
        env['G1_REPO_ROOT']=ROOT
        env['G1_VALIDATOR_SOURCE']=anchor
        env['G1_VALIDATOR_SOURCE_KIND']=('external-pin' if env_anchor else 'target-commit')
        env['G1_RUNNER_SOURCE']=os.environ.get('G1_RUNNER_COMMIT','') or 'working-tree'
        env.pop('PYTHONPATH',None)
        cmd=[sys.executable,'-I',os.path.join(tmp,'tools','g1_validate.py')]+sys.argv[1:]
        print(f"[trusted-verify] Running validator extracted from {anchor[:12]} in isolation: {tmp}")
        p=subprocess.run(cmd,env=env,cwd=tmp)
        return p.returncode
    finally:
        shutil.rmtree(tmp,ignore_errors=True)

if __name__=='__main__':
    sys.exit(main())
