#!/usr/bin/env python3
"""g1_trusted_verify.py — 信頼された実行入口（G1b 承認の検証はこれを使う）

現在の checkout にある validator を実行してはならない。
validator を書き換えて検査を無効化すれば、その validator は自分の改変を報告しない
（自己検査の原理的限界）。

このランナーは:
  1. 承認記録 tests/approvals/g1.yaml を最後に変更した commit A を git から特定する
  2. A の署名を検証する（signed-commit / signed-tag）
  3. A が対象 commit C の子孫であり、C..A の変更が承認記録だけであることを確認する
  4. **validator は A から取らない**。
     G1_VALIDATOR_COMMIT（40 桁完全 SHA。CI が外部固定する trust anchor）が
     あればそこから、なければ C から取り出す
  5. 取り出した validator を `python -I`（隔離モード）で実行し、
     検査対象リポジトリは G1_REPO_ROOT で渡す
  6. 現在の checkout の tools/ は sys.path に一切載せない

これにより「承認者が検査器を定義する」「改変された validator が自分を検査する」を断つ。

このランナー自身の改変は、ランナーの中では防げない。
**CI は tools/g1_ci_verify.sh のように、固定 SHA からランナーを取り出して
実行する外部ラッパーを使うこと。** その場合の検査対象は G1_REPO_ROOT で渡す。

  環境変数:
    G1_REPO_ROOT         検査対象リポジトリ（省略時はこのスクリプトの位置から導出）
    G1_VALIDATOR_COMMIT  validator の取得元（40 桁完全 SHA のみ）
    G1_RUNNER_COMMIT     このランナーの取得元 SHA（監査レポートに記録するだけ）

  使い方:  python3 tools/g1_trusted_verify.py [--offline]
  終了コード: 0 = ブロッキング違反なし / 1 = あり / 2 = 検証の前提が崩れている
"""
import os,sys
# ★ 隔離モードでなければ自分自身を `python -I` で起動し直す。
#   -I は PYTHONPATH と user site-packages を無効化する。
#   sys.path[0]（スクリプトの位置 = tools/）の削除だけでは
#   `PYTHONPATH=. python tools/g1_trusted_verify.py` で
#   リポジトリ直下の未署名 yaml.py が署名検証前に実行されてしまう。
#   この再起動はサードパーティを一切 import する前に行う。
if not sys.flags.isolated:
    os.execv(sys.executable,[sys.executable,'-I',os.path.abspath(__file__)]+sys.argv[1:])
# -I でも sys.path[0] にはスクリプトの位置が入るので、これも外す
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
        die(f"検査対象 {ROOT} が git リポジトリでない。G1_REPO_ROOT を指定すること")
    r=git('rev-parse','--verify','--quiet','HEAD')
    if r.returncode!=0: die("git HEAD がない。承認対象を固定できない")
    print(f"[trusted-verify] 検査対象リポジトリ: {ROOT}")

    lg=git('log','-1','--format=%H','--',APPROVAL_REL)
    sig=lg.stdout.strip() if lg.returncode==0 else ''
    if not sig:
        die(f"{APPROVAL_REL} が commit されていない。G1b の承認がまだ行われていない")

    # 署名種別は承認記録から読むが、記録自体はまだ信用しない（署名検証の入口を選ぶだけ）
    import yaml   # sys.path は冒頭で sanitize 済み
    blob=git('show',f'{sig}:{APPROVAL_REL}',binary=True)
    if blob.returncode!=0: die("署名済み commit から承認記録を読み出せない")
    peek=yaml.safe_load(blob.stdout.decode('utf-8')) or {}
    ev=peek.get('evidence') or {}
    kind=ev.get('kind')

    if kind=='signed-tag':
        tag=ev.get('tag')
        if not tag: die("evidence.kind=signed-tag には evidence.tag が必須")
        if git('verify-tag',str(tag)).returncode!=0: die(f"tag {tag} の署名検証に失敗")
        rt=git('rev-list','-n','1',str(tag))
        if rt.returncode!=0 or rt.stdout.strip()!=sig:
            die(f"tag {tag} が承認記録を含む commit {sig[:12]} を指していない")
    elif kind=='signed-commit':
        if git('verify-commit',sig).returncode!=0:
            die(f"承認 commit {sig[:12]} の署名検証に失敗")
    else:
        die("evidence.kind は signed-commit / signed-tag のいずれか")

    print(f"[trusted-verify] 署名済み承認 commit: {sig[:12]} ({kind})")

    # ------------------------------------------------------------------
    # ★ validator を承認 commit A から取ってはならない。
    #   A の署名者が承認記録と一緒に validator を弱体化できてしまう。
    #   取得元の優先順位:
    #     1) G1_VALIDATOR_COMMIT（CI 設定で外部から固定する trust anchor）
    #     2) 承認対象 commit C（レビュアーが実際に読んだ成果物）
    #   さらに C..A の変更を承認記録だけに制限し、A が C の子孫であることを要求する。
    # ------------------------------------------------------------------
    C=str(peek.get('target_commit') or '')
    if not is_full_sha(C):
        die("target_commit は 40 桁の完全な SHA-1 でなければならない")
    if git('rev-parse','--verify','--quiet',C+'^{commit}').stdout.strip()!=C:
        die(f"target_commit {C[:12]} が commit として完全一致で解決できない")
    if git('merge-base','--is-ancestor',C,sig).returncode!=0:
        die(f"承認 commit {sig[:12]} が対象 commit {C[:12]} の子孫でない")

    changed=sorted(x for x in git('diff','--name-only',f'{C}..{sig}').stdout.split() if x)
    if changed!=[APPROVAL_REL]:
        die(f"C..A の変更が承認記録だけに限定されていない: {changed[:6]}\n"
            f"          承認 commit は {APPROVAL_REL} の追加のみを含むこと")

    env_anchor=os.environ.get('G1_VALIDATOR_COMMIT')
    if env_anchor:
        # ★ HEAD / main のような可変 ref を trust anchor にしてはならない。
        #   40 桁完全 SHA を要求し、rev-parse の出力と完全一致することを検査する。
        if not is_full_sha(env_anchor):
            die("G1_VALIDATOR_COMMIT は 40 桁の完全な SHA-1 でなければならない"
                f"（可変 ref は不可。与えられた値: {env_anchor!r}）")
        r=git('rev-parse','--verify','--quiet',env_anchor+'^{commit}')
        if r.returncode!=0 or r.stdout.strip()!=env_anchor:
            die(f"G1_VALIDATOR_COMMIT {env_anchor[:12]} が commit として完全一致で解決できない")
        anchor=env_anchor; anchor_src='G1_VALIDATOR_COMMIT (外部固定)'
    else:
        anchor=C; anchor_src='target_commit（CI では G1_VALIDATOR_COMMIT で固定すること）'
    print(f"[trusted-verify] validator の取得元: {anchor[:12]} / {anchor_src}")

    # A の tree から validator を取り出して隔離実行する
    tmp=tempfile.mkdtemp(prefix='g1-trusted-')
    try:
        os.makedirs(os.path.join(tmp,'tools'),exist_ok=True)
        for rel in EXTRACT:
            b=git('show',f'{anchor}:{rel}',binary=True)
            if b.returncode!=0: die(f"{rel} が {anchor[:12]} に存在しない")
            with open(os.path.join(tmp,rel),'wb') as f: f.write(b.stdout)
        env=dict(os.environ)
        env['G1_REPO_ROOT']=ROOT
        env['G1_VALIDATOR_SOURCE']=anchor
        env['G1_VALIDATOR_SOURCE_KIND']=('external-pin' if env_anchor else 'target-commit')
        env['G1_RUNNER_SOURCE']=os.environ.get('G1_RUNNER_COMMIT','') or 'working-tree'
        env.pop('PYTHONPATH',None)
        cmd=[sys.executable,'-I',os.path.join(tmp,'tools','g1_validate.py')]+sys.argv[1:]
        print(f"[trusted-verify] 承認 commit から取り出した validator を隔離実行: {tmp}")
        p=subprocess.run(cmd,env=env,cwd=tmp)
        return p.returncode
    finally:
        shutil.rmtree(tmp,ignore_errors=True)

if __name__=='__main__':
    sys.exit(main())
