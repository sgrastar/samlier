#!/usr/bin/env python3
"""g1_docgen.py — generate documentation from tests/coverage.yaml

Generated artifacts:
  1. docs/04-requirement-coverage.md         … written in full by this script
  2. <!--g1:KEY-->…<!--/g1--> in other docs/*.md … numeric counts

No network or authoring input is required. The committed coverage.yaml is sufficient
to regenerate the same output in another checkout.

Usage:  python3 tools/g1_docgen.py [--check]
        --check does not overwrite files and returns an exit code indicating drift.
"""
import os,sys,re,yaml,datetime
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
A("# 04. Requirement Coverage Map (Generated)");A("")
A("> ⚠ **Generated from `tests/coverage.yaml`; do not edit manually.**")
A("> Regenerate: `python3 tools/g1_docgen.py` (no network / authoring input required)")
A(f"> G1 state: **{cov.get('g1_state')}**");A("")
A(f"Document: **{P['title']}, Version {P['version']} ({P['date']})**")
A("")
A(f"{P['url']}")
A(f"Source digest: `{P.get('source_digest')}`");A("")
A("Validation: `python3 tools/g1_validate.py` → `build/spec-reconcile-report.json`");A("")
A("## Summary");A("")
A("| Metric | Value |");A("|---|---|")
A(f"| Requirements | {len(reqs)} |")
A(f"| Obligations | {len(obs)} |")
A(f"| MUST_CLASS | {sum(v for k,v in lv.items() if k in MUSTC)} |")
A(f"| SHOULD_CLASS | {lv['SHOULD']+lv['SHOULD_NOT']+lv['RECOMMENDED']} |")
A(f"| MAY_CLASS | {lv['MAY']+lv['OPTIONAL']} |")
A(f"| Conditional obligations | {sum(1 for _,o in obs if o.get('condition'))} |")
A(f"| IdP profile | {len(idp)} obligations (Core {core('idp',idp)} / Full {len(idp)-core('idp',idp)}) |")
A(f"| SP profile | {len(sp)} obligations (Core {core('sp',sp)} / Full {len(sp)-core('sp',sp)}) |")
A(f"| Non-normative (italic) spans | {sum(len(r.get('non_normative_spans') or []) for r in reqs)} |")
A("")
A("**Testability**");A("")
A("| Symbol | Meaning | Count |");A("|---|---|---|")
for k,d in [('AUTOMATED','Completed through direct Suite-to-target communication (no browser)'),('BROWSER','Requires the user\'s browser'),
            ('ATTESTED','The user attests to behavior inside the target'),('CONFIG','Run after requesting a configuration change on the target'),
            ('NOT_OBSERVABLE','Fundamentally unverifiable externally; no case is created')]:
    A(f"| `{k}` | {d} | {tb.get(k,0)} |")
A("")
A("**Verdict notes**");A("")
A("- The sole source of verdict levels is `tests/coverage.yaml`.")
A("- Cases return `outcome`; the Evaluator maps it to Verdict by consulting `level` ([03 §4](03-test-model.md)).")
A("- `NOT_APPLICABLE` is limited to role mismatch and false conditional-obligation conditions. Anything that could not be executed is `NOT_VERIFIED`.")
A("- **Core / Full is Samlier's own classification**; the IIP source does not make this distinction.")
A("")
# Include both forward and reverse linked_obligations references in docs/04.
LINKS={o['key']:[lk for lk in (o.get('linked_obligations') or []) if isinstance(lk,dict)] for _,o in obs}
BACK={}
for _k,_ls in LINKS.items():
    for _lk in _ls: BACK.setdefault(_lk['obligation'],[]).append((_k,_lk['kind']))
VARN={o['key']:len(o.get('required_variants') or []) for _,o in obs}
A("## Requirements and Obligations");A("")
cur=None
for r in reqs:
    if r['section']!=cur:
        cur=r['section']; A(f"### {r['section']} {r['section_name']}"); A("")
    nn=len(r.get('non_normative_spans') or [])
    A(f"#### {r['id']}");A("")
    A(f"[Source]({P['url']}{r['anchor']}) / Section digest `{r['source_section_digest'][:19]}…` / Section length {r['source_section_length']} / Non-normative spans {nn}")
    A("")
    A("| Obligation | Level | Role | Testability | Condition | Core/Full | Summary |");A("|---|---|---|---|---|---|---|")
    for o in r['obligations']:
        c=o.get('condition')
        cs=f"`{c['predicate']}`<br>({c['predicate_kind']})" if c else "—"
        la='/'.join(sorted(set((o.get('level_assignment') or {}).values())))
        A(f"| `{o['key']}` | {o['level']} | {'/'.join(o['roles'])} | `{o['testability']}` | {cs} | {la} | {o['summary_en']} |")
    A("")
    for o in r['obligations']:
        A(f"<details><summary><code>{o['key']}</code> details</summary>");A("")
        if o['testability']=='NOT_OBSERVABLE':
            A(f"- **Reason not observable**: {o.get('not_observable_reason_en')}")
        else:
            A("- **Required variants**:")
            for v in o.get('required_variants') or []:
                A(f"  - `{v['id']}` {v['description_en']}")
        if o.get('controls'):
            A("- **Controls (negative controls)**:")
            for v in o['controls']: A(f"  - {v}")
        for lk in LINKS.get(o['key'],[]):
            A(f"- **Linked obligation**: `{lk['obligation']}` (`{lk['kind']}` / {VARN.get(lk['obligation'],0)} variants) — {lk['note_en']}")
        for src,kind in BACK.get(o['key'],[]):
            A(f"- **Referenced by**: `{src}` incorporates this obligation via `{kind}`. Editing this obligation's variants also affects `{src}` cases.")
        if o.get('configuration_failure_semantics'):
            A(f"- **Configuration failure semantics**: `{o['configuration_failure_semantics']}`")
        if o.get('applicability_note_en'): A(f"- **Applicability**: {o['applicability_note_en']}")
        if o.get('references_spec'): A(f"- **Referenced specification**: `{o['references_spec']}`")
        if o.get('reference_derivation_note'): A(f"- **Reference derivation**: {o['reference_derivation_note']}")
        if o.get('exclusion_clause_en'): A(f"- **Exclusion**: {o['exclusion_clause_en']}")
        for ev in o.get('reference_evidence') or []:
            if ev.get('basis_en'):
                locator = f"; locator: `{ev['locator']}`" if ev.get('locator') else ""
                A(f"- **Reference basis ({ev.get('spec', 'unspecified')})**{locator}: {ev['basis_en']}")
        if o.get('notes_en'): A(f"- **Notes**: {o['notes_en']}")
        if o.get('open_question_en'): A(f"- ⚠ **Open question**: {o['open_question_en']}")
        A("- **source_clauses**: "+" , ".join(
            f"`[{c['start']}, {c['end']})` `{c['digest'][:19]}…`"+(f" ⚠{c['occurrences']} matching occurrences" if c.get('occurrences',1)>1 else "")
            for c in o['source_clauses']))
        rv=o['review']
        A(f"- **review**: `{rv['state']}` / reviewer: `{rv.get('reviewer')}` / approved_at: `{rv.get('approved_at')}`")
        A("");A("</details>");A("")
A("## G1 Status");A("")
pend=sum(1 for _,o in obs if o['review']['state']!='APPROVED')
opens=[o['key'] for _,o in obs if o.get('open_question_en')]
A("```")
A(f"g1_state       : {cov.get('g1_state')}")
A(f"obligations    : {len(obs)}")
A(f"unapproved      : {pend}")
A(f"open questions  : {len(opens)}{' '+str(opens) if opens else ''}")
A("```");A("")
A("The author has not populated `reviewer` / `approved_at`.")
A("Test implementation must not begin until another reviewer approves it after **directly comparing the source and `tests/coverage.yaml`**.")
A("")
out='\n'.join(M)
path=os.path.join(ROOT,'docs','04-requirement-coverage.md')

# ---------------------------------------------------------------------------
# Numeric substitutions for other documents.
#   Do not hard-code counts in prose: adding an obligation otherwise leaves stale
#   values in multiple files. Markers are <!--g1:KEY-->value<!--/g1-->;
#   KEY must be present in the table below.
# ---------------------------------------------------------------------------
NOBS_KEYS=[o['key'] for _,o in obs if o['testability']=='NOT_OBSERVABLE']
STATS={
  'requirements'      : str(len(reqs)),
  'obligations'       : str(len(obs)),
  'case_target'       : str(len(obs)-len(NOBS_KEYS)),
  'not_observable'    : str(len(NOBS_KEYS)),
  'not_observable_keys': ' / '.join(f'`{k}`' for k in NOBS_KEYS),
  'specs'             : str(len(specs['specs'])),
  'predicates'        : str(len(yaml.safe_load(open(os.path.join(ROOT,'tests','predicates.yaml'),encoding='utf-8'))['predicates'])),
  'multi_clause'      : str(sum(1 for _,o in obs if len(o['source_clauses'])>1)),
  'variants'          : str(sum(len(o.get('required_variants') or []) for _,o in obs)),
  'conditional'       : str(sum(1 for _,o in obs if o.get('condition'))),
  'open_questions'    : str(len([1 for _,o in obs if o.get('open_question_en')])),
  'unapproved'        : str(sum(1 for _,o in obs if o['review']['state']!='APPROVED')),
}
for k in ('AUTOMATED','BROWSER','ATTESTED','CONFIG','NOT_OBSERVABLE'):
    STATS['tb_'+k.lower()]=str(tb.get(k,0))
MARK=re.compile(r'<!--g1:([a-z_]+)-->(.*?)<!--/g1-->',re.S)
TARGETS=[os.path.join(ROOT,d,f) for d,f in
         [('docs',x) for x in sorted(os.listdir(os.path.join(ROOT,'docs')))
          # 04 is generated in full; 11 is a review record frozen at its historical counts.
          if x.endswith('.md') and x not in ('04-requirement-coverage.md','11-review-log.md')]
         +[('tools','ci-stages.md'),('tools','README.md'),('.','AGENTS.md')]]
unknown=[]
def render(txt,rel):
    def f(m):
        k=m.group(1)
        if k not in STATS:
            unknown.append(f"{rel}: undefined marker g1:{k}"); return m.group(0)
        return f'<!--g1:{k}-->{STATS[k]}<!--/g1-->'
    return MARK.sub(f,txt)
stale=[]
written=[]
for t in TARGETS:
    if not os.path.exists(t): continue
    rel=os.path.relpath(t,ROOT)
    cur=open(t,encoding='utf-8').read()
    new=render(cur,rel)
    if new==cur: continue
    if CHECK: stale.append(rel)
    else: open(t,'w',encoding='utf-8').write(new); written.append(rel)

if CHECK:
    cur_txt=open(path,encoding='utf-8').read() if os.path.exists(path) else ''
    ok = cur_txt==out and not stale and not unknown
    print("docs/04 matches coverage.yaml" if cur_txt==out
          else "docs/04 does not match the content generated from coverage.yaml")
    if stale:   print("Numeric markers that do not match coverage.yaml: "+", ".join(stale))
    if unknown: print("\n".join(unknown))
    sys.exit(0 if ok else 1)
if unknown: raise SystemExit("\n".join(unknown))
open(path,'w',encoding='utf-8').write(out)
print(f"wrote docs/04-requirement-coverage.md ({len(M)} lines) from coverage.yaml")
print("updated markers in: "+(", ".join(written) if written else "(none)"))
