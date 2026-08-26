#!/usr/bin/env python3
"""g1_trusted_verify.py — 信頼された実行入口（G1b 承認の検証はこれを使う）

現在の checkout にある validator を実行してはならない。
validator を書き換えて検査を無効化すれば、その validator は自分の改変を報告しない
（自己検査の原理的限界）。

このランナーは:
  1. 承認記録 tests/approvals/g1.yaml を最後に変更した commit A を git から特定する
  2. A の署名を検証する（signed-commit / signed-tag）
  3. **A の tree から** validator 一式を隔離ディレクトリに取り出す
  4. 取り出した validator を `python -I`（隔離モード）で実行し、
     検査対象リポジトリは G1_REPO_ROOT で渡す
  5. 現在の checkout の tools/ は sys.path に一切載せない

これにより「改変された validator が自分を検査する」構図を断つ。
このランナー自身が改変される可能性は残るが、ランナーは短く、
CI 側では承認済み commit から取り出したものを使うこと。

  使い方:  python3 tools/g1_trusted_verify.py [--offline]
  終了コード: 0 = ブロッキング違反なし / 1 = あり / 2 = 検証の前提が崩れている
"""
import os,sys
# ★ 最初に「スクリプト自身のディレクトリ」を sys.path から外す。
#   python は sys.path[0] にスクリプトの位置を入れるため、この処理の前に
#   サードパーティを import すると tools/yaml.py 等に shadow される。
if sys.path and os.path.basename(sys.path[0] or '')=='tools':
    del sys.path[0]
import subprocess,tempfile,shutil,json

ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APPROVAL_REL='tests/approvals/g1.yaml'
EXTRACT=('tools/g1_validate.py','tools/g1_extract.py')

def git(*a,binary=False):
    return subprocess.run(['git','-C',ROOT]+list(a),capture_output=True,text=not binary)

def die(msg,code=2):
    print(f"[trusted-verify] {msg}",file=sys.stderr); sys.exit(code)

def main():
    r=git('rev-parse','--verify','--quiet','HEAD')
    if r.returncode!=0: die("git HEAD がない。承認対象を固定できない")

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

    # A の tree から validator を取り出して隔離実行する
    tmp=tempfile.mkdtemp(prefix='g1-trusted-')
    try:
        os.makedirs(os.path.join(tmp,'tools'),exist_ok=True)
        for rel in EXTRACT:
            b=git('show',f'{sig}:{rel}',binary=True)
            if b.returncode!=0: die(f"{rel} が承認 commit に存在しない")
            with open(os.path.join(tmp,rel),'wb') as f: f.write(b.stdout)
        env=dict(os.environ)
        env['G1_REPO_ROOT']=ROOT
        env.pop('PYTHONPATH',None)
        cmd=[sys.executable,'-I',os.path.join(tmp,'tools','g1_validate.py')]+sys.argv[1:]
        print(f"[trusted-verify] 承認 commit から取り出した validator を隔離実行: {tmp}")
        p=subprocess.run(cmd,env=env,cwd=tmp)
        return p.returncode
    finally:
        shutil.rmtree(tmp,ignore_errors=True)

if __name__=='__main__':
    sys.exit(main())
