#!/usr/bin/env python3
"""g1_docgen.py — tests/coverage.yaml から docs/04-requirement-coverage.md を生成する

★ ネットワーク不要・authoring 入力不要。コミット済みの coverage.yaml だけで再生成できる。
   別 checkout でも同じ出力になる（再現性の担保はこのスクリプトが持つ）。

  使い方:  python3 tools/g1_docgen.py [--check]
           --check は上書きせず、既存ファイルとの差分の有無だけを終了コードで返す
"""
import os,sys,yaml,datetime
from collections import Counter
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
cov=yaml.safe_load(open(os.path.join(ROOT,'tests','coverage.yaml'),encoding='utf-8'))
specs=yaml.safe_load(open(os.path.join(ROOT,'tests','specs.yaml'),encoding='utf-8'))
CHECK='--check' in sys.argv
P=specs['specs'][cov['spec']]
reqs=cov['requirements']; obs=[(r['id'],o) for r in reqs for o in r['obligations']]
MUSTC={'MUST','MUST_NOT','REQUIRED'}
lv=Counter(o['level'] for _,o in obs); tb=Counter(o['testability'] for _,o in obs)
def rl(role): return [(r,o) for r,o in obs if role in o['roles']]
def core(role,l): return sum(1 for r,o in l if (o.get('level_assignment') or {}).get(role)=='core')
idp,sp=rl('idp'),rl('sp')
M=[]
A=M.append
A("# 04. 要件カバレッジマップ（生成物）");A("")
A("> ⚠ **`tests/coverage.yaml` からの生成物です。手で編集しないでください。**")
A("> 再生成: `python3 tools/g1_docgen.py`（ネットワーク不要 / authoring 入力不要）")
A(f"> G1 状態: **{cov.get('g1_state')}**");A("")
A(f"対象文書: **{P['title']}, Version {P['version']} ({P['date']})**  ")
A(f"{P['url']}  ")
A(f"原文ダイジェスト: `{P.get('source_digest')}`");A("")
A("検証: `python3 tools/g1_validate.py` → `build/spec-reconcile-report.json`");A("")
A("## サマリ");A("")
A("| 指標 | 値 |");A("|---|---|")
A(f"| 要件 | {len(reqs)} |")
A(f"| 義務（obligation） | {len(obs)} |")
A(f"| うち MUST_CLASS | {sum(v for k,v in lv.items() if k in MUSTC)} |")
A(f"| うち SHOULD_CLASS | {lv['SHOULD']+lv['SHOULD_NOT']+lv['RECOMMENDED']} |")
A(f"| うち MAY_CLASS | {lv['MAY']+lv['OPTIONAL']} |")
A(f"| 条件付き義務 | {sum(1 for _,o in obs if o.get('condition'))} |")
A(f"| IdP プロファイル | {len(idp)} 義務（Core {core('idp',idp)} / Full {len(idp)-core('idp',idp)}） |")
A(f"| SP プロファイル | {len(sp)} 義務（Core {core('sp',sp)} / Full {len(sp)-core('sp',sp)}） |")
A(f"| 非規範（イタリック）スパン | {sum(len(r.get('non_normative_spans') or []) for r in reqs)} |")
A("")
A("**Testability**");A("")
A("| 記号 | 意味 | 件数 |");A("|---|---|---|")
for k,d in [('AUTOMATED','Suite と対象の直接通信で完結（ブラウザ不要）'),('BROWSER','利用者のブラウザが必要'),
            ('ATTESTED','対象内部の挙動を利用者が申告'),('CONFIG','対象側の設定変更を依頼したうえで実行'),
            ('NOT_OBSERVABLE','外部から原理的に検証不能。ケースを作らない')]:
    A(f"| `{k}` | {d} | {tb.get(k,0)} |")
A("")
A("**判定に関する注意**");A("")
A("- 判定レベルの唯一の出典は `tests/coverage.yaml` です")
A("- ケースは `outcome` を返し、Verdict への変換は Evaluator が `level` を見て行います（[03 §4](03-test-model.md)）")
A("- `NOT_APPLICABLE` は「役割違い」と「条件付き義務の条件が偽」のみ。実行できなかったものは `NOT_VERIFIED` です")
A("- **Core / Full は Samlier 独自の分類**であり、IIP 原文にこの区別はありません")
A("")
A("## 要件と義務");A("")
cur=None
for r in reqs:
    if r['section']!=cur:
        cur=r['section']; A(f"### {r['section']} {r['section_name']}"); A("")
    nn=len(r.get('non_normative_spans') or [])
    A(f"#### {r['id']}");A("")
    A(f"[原文]({P['url']}{r['anchor']}) ／ 節ダイジェスト `{r['source_section_digest'][:19]}…` ／ 節長 {r['source_section_length']} ／ 非規範スパン {nn}")
    A("")
    A("| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |");A("|---|---|---|---|---|---|---|")
    for o in r['obligations']:
        c=o.get('condition')
        cs=f"`{c['predicate']}`<br>({c['predicate_kind']})" if c else "—"
        la='/'.join(sorted(set((o.get('level_assignment') or {}).values())))
        A(f"| `{o['key']}` | {o['level']} | {'/'.join(o['roles'])} | `{o['testability']}` | {cs} | {la} | {o['summary_ja']} |")
    A("")
    for o in r['obligations']:
        A(f"<details><summary><code>{o['key']}</code> の詳細</summary>");A("")
        if o['testability']=='NOT_OBSERVABLE':
            A(f"- **検証不能の理由**: {o.get('not_observable_reason_ja')}")
        else:
            A("- **必要な variant**:")
            for v in o.get('required_variants') or []: A(f"  - {v}")
        if o.get('controls'):
            A("- **対照（negative control）**:")
            for v in o['controls']: A(f"  - {v}")
        if o.get('configuration_failure_semantics'):
            A(f"- **設定不能時の意味**: `{o['configuration_failure_semantics']}`")
        if o.get('applicability_note_ja'): A(f"- **適用範囲**: {o['applicability_note_ja']}")
        if o.get('references_spec'): A(f"- **参照先仕様**: `{o['references_spec']}`")
        if o.get('notes_ja'): A(f"- **注記**: {o['notes_ja']}")
        if o.get('open_question_ja'): A(f"- ⚠ **未解決**: {o['open_question_ja']}")
        A("- **source_clauses**: "+" , ".join(
            f"`[{c['start']}, {c['end']})` `{c['digest'][:19]}…`"+(f" ⚠{c['occurrences']} 箇所一致" if c.get('occurrences',1)>1 else "")
            for c in o['source_clauses']))
        rv=o['review']
        A(f"- **review**: `{rv['state']}` / reviewer: `{rv.get('reviewer')}` / approved_at: `{rv.get('approved_at')}`")
        A("");A("</details>");A("")
A("## G1 の状態");A("")
pend=sum(1 for _,o in obs if o['review']['state']!='APPROVED')
opens=[o['key'] for _,o in obs if o.get('open_question_ja')]
A("```")
A(f"g1_state       : {cov.get('g1_state')}")
A(f"obligations    : {len(obs)}")
A(f"未承認         : {pend}")
A(f"未解決 open Q  : {len(opens)}{' '+str(opens) if opens else ''}")
A("```");A("")
A("作成者は `reviewer` / `approved_at` を埋めていません。")
A("別のレビュアーが**原文と `tests/coverage.yaml` を直接照合**して承認するまで、テスト実装に着手しません。")
A("")
out='\n'.join(M)
path=os.path.join(ROOT,'docs','04-requirement-coverage.md')
if CHECK:
    cur_txt=open(path,encoding='utf-8').read() if os.path.exists(path) else ''
    ok = cur_txt==out
    print("docs/04 は coverage.yaml と一致しています" if ok else "docs/04 が coverage.yaml から生成した内容と一致しません")
    sys.exit(0 if ok else 1)
open(path,'w',encoding='utf-8').write(out)
print(f"wrote docs/04-requirement-coverage.md ({len(M)} lines) from coverage.yaml")
