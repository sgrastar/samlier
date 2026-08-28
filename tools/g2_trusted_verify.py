#!/usr/bin/env python3
"""Trusted entry point for signed G2 design approval verification."""
import os
import sys

if not sys.flags.isolated:
    os.execv(sys.executable, [sys.executable, '-I', os.path.abspath(__file__), *sys.argv[1:]])
if sys.path and os.path.basename(sys.path[0] or '') == 'tools':
    del sys.path[0]

import pathlib
import shutil
import subprocess
import tempfile

import yaml

ROOT = pathlib.Path(os.environ.get('G2_REPO_ROOT') or pathlib.Path(__file__).resolve().parents[1]).resolve()
APPROVAL_REL = 'tests/approvals/g2.yaml'

def git(*args, binary=False):
    return subprocess.run(['git', '-C', str(ROOT), *args], capture_output=True,
                          text=not binary, check=False)

def die(message):
    print('[g2-trusted-verify] ' + message, file=sys.stderr)
    raise SystemExit(2)

def exact_commit(value):
    value = str(value or '')
    if len(value) != 40 or any(char not in '0123456789abcdef' for char in value):
        return False
    resolved = git('rev-parse', '--verify', '--quiet', value + '^{commit}')
    return resolved.returncode == 0 and resolved.stdout.strip() == value

def main():
    if git('rev-parse', '--git-dir').returncode != 0:
        die(f'{ROOT} is not a git repository')
    log = git('log', '-1', '--format=%H', '--', APPROVAL_REL)
    approval_commit = log.stdout.strip() if log.returncode == 0 else ''
    if not approval_commit:
        die(f'{APPROVAL_REL} is not committed; G2 approval is pending')
    if git('verify-commit', approval_commit).returncode != 0:
        die(f'signature verification failed for approval commit {approval_commit[:12]}')
    blob = git('show', f'{approval_commit}:{APPROVAL_REL}', binary=True)
    if blob.returncode != 0:
        die('cannot read approval record from signed commit')
    approval = yaml.safe_load(blob.stdout.decode()) or {}
    target = approval.get('target_commit')
    if not exact_commit(target):
        die('target_commit is not an exact 40-character commit SHA')
    if git('merge-base', '--is-ancestor', target, approval_commit).returncode != 0:
        die('approval commit is not a descendant of target commit')
    changed = sorted(git('diff', '--name-only', f'{target}..{approval_commit}').stdout.split())
    if changed != [APPROVAL_REL]:
        die(f'C..A changes are not limited to {APPROVAL_REL}: {changed[:6]}')

    external = os.environ.get('G2_VALIDATOR_COMMIT')
    anchor = external or target
    if not exact_commit(anchor):
        die('G2_VALIDATOR_COMMIT must be an exact 40-character commit SHA')
    source_kind = 'external-pin' if external else 'target-commit'
    temporary = pathlib.Path(tempfile.mkdtemp(prefix='g2-trusted-'))
    try:
        tools = temporary / 'tools'
        tools.mkdir()
        extracted = git('show', f'{anchor}:tools/g2_validate.py', binary=True)
        if extracted.returncode != 0:
            die(f'tools/g2_validate.py does not exist at {anchor[:12]}')
        (tools / 'g2_validate.py').write_bytes(extracted.stdout)
        environment = dict(os.environ)
        environment.pop('PYTHONPATH', None)
        environment['G2_REPO_ROOT'] = str(ROOT)
        environment['G2_VALIDATOR_SOURCE'] = anchor
        environment['G2_VALIDATOR_SOURCE_KIND'] = source_kind
        environment['G2_RUNNER_SOURCE'] = os.environ.get('G2_RUNNER_COMMIT') or 'working-tree'
        print(f'[g2-trusted-verify] approval={approval_commit[:12]} target={target[:12]} validator={anchor[:12]}')
        process = subprocess.run([sys.executable, '-I', str(tools / 'g2_validate.py')],
                                 cwd=temporary, env=environment, check=False)
        return process.returncode
    finally:
        shutil.rmtree(temporary, ignore_errors=True)

if __name__ == '__main__':
    raise SystemExit(main())
