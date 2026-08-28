#!/usr/bin/env python3
"""g1_validate.py — Independent validation of G1 artifacts (:specReconcile)

★ Independent of generation: it only reads and reconciles committed artifacts and
   never writes values back. It uses the fetched (or cached) source text and
   compares its digest with the value recorded in specs.yaml.

  Usage:  python3 tools/g1_validate.py [--offline | --structural-only]

    --offline          Use only cached source text (do not fetch it).
    --structural-only  Do not inspect source text; run structural checks only.
                       ★ For CI g1Check; no CI-side exclusion list is needed.
  Dependency: PyYAML
  Output: build/spec-reconcile-report.json  exit code 0=PASS / 1=FAIL
"""
import os,re,sys,json,html,hashlib,unicodedata,datetime,uuid,urllib.request
# ★ Do not add tools/ to sys.path. An untracked tools/yaml.py or similar could
#   shadow a standard/third-party module and run arbitrary code before signature verification.
try:
    import yaml
except ImportError:
    sys.exit("PyYAML is required: .venv/bin/pip install -r tools/requirements.txt")
# Load g1_extract by explicit path from the same directory as this validator.
import importlib.util as _ilu
_here=os.path.dirname(os.path.abspath(__file__))
_spec=_ilu.spec_from_file_location('g1_extract_local',os.path.join(_here,'g1_extract.py'))
X=_ilu.module_from_spec(_spec); _spec.loader.exec_module(X)

ROOT=os.environ.get('G1_REPO_ROOT') or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TESTS=os.path.join(ROOT,'tests'); BUILD=os.path.join(ROOT,'build')
CACHE=os.path.join(BUILD,'spec-cache'); os.makedirs(CACHE,exist_ok=True)
STRUCT_ONLY='--structural-only' in sys.argv
OFFLINE=('--offline' in sys.argv) or STRUCT_ONLY
MODE='offline' if OFFLINE else 'network' 
NOW=datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()

R=[]
def check(cid,desc,passed,detail=""):
    R.append(dict(id=cid,description=desc,result="PASS" if passed else "FAIL",detail=str(detail)[:400]))
    return passed

def load(name):
    with open(os.path.join(TESTS,name),encoding='utf-8') as f: return yaml.safe_load(f)
specs=load('specs.yaml'); cov=load('coverage.yaml'); preds=load('predicates.yaml')

# ---- Fetch source text (the validator itself never writes digests) ----
primary=specs['specs'][cov['spec']]
raw=None
if not STRUCT_ONLY:
    try:
        raw,_=X.fetch(ROOT,cov['spec'],primary['url'],mode=MODE)
    except Exception as e:
        raw=None; check("SR-00","primary spec can be fetched",False,f"{type(e).__name__}: {e}")
if STRUCT_ONLY:
    pass
elif raw is None:
    check("SR-01","source text can be fetched (--offline with no cache)",False,"no source");
else:
    got='sha256:'+hashlib.sha256(raw).hexdigest()
    check("SR-01","fetched source digest matches the value recorded in specs.yaml",
          got==primary.get('source_digest'), f"fetched={got} recorded={primary.get('source_digest')}")
    check("SR-01b","coverage.yaml source_digest matches specs.yaml",
          cov.get('source_digest')==primary.get('source_digest'), cov.get('source_digest'))

reqs=cov['requirements']
obs=[(r['id'],o) for r in reqs for o in r['obligations']]

# ---- Normalization (reproduce the procedure declared by coverage.yaml) ----
SEC={}
if raw is not None:
    t=raw.decode('utf-8')
    t=re.sub(r'(?is)<(script|style).*?</\1>','',t)
    t=t.replace('<em>','\x01').replace('</em>','\x02')
    t=re.sub(r'(?i)<em [^>]*>','\x01',t)
    t=re.sub(r'(?i)<(/?)(p|div|li|ul|ol|table|tr|td|th|h[1-6]|dt|dd|pre|br)[^>]*>','\n',t)
    t=re.sub(r'(?s)<[^>]+>','',t); t=html.unescape(t); t=unicodedata.normalize('NFC',t)
    t='\n'.join(l for l in (re.sub(r'[ \t]+',' ',x).strip() for x in t.split('\n')) if l)
    LAB=[(m.group(1),m.start()) for m in re.finditer(r'(?m)^\[(IIP-[A-Z]+\d{2})\]$',t) if m.group(1)!='IIP-EXAMPLE01']
    CUT=re.compile(r'^(\d+(\.\d+)*\.\s|Key Rollover$|Algorithm Support$|Avoiding Common Errors$|Metadata Exchange$|Metadata Usage$)')
    for k,(rid,p) in enumerate(LAB):
        end=LAB[k+1][1] if k+1<len(LAB) else t.rindex('\n5. References')
        body=t[p+len(rid)+2:end].strip('\n'); keep=[]
        for ln in body.split('\n'):
            if CUT.match(ln): break
            keep.append(ln)
        raws='\n'.join(keep).strip()
        out=[];sp=[];d=0;st=None
        for ch in raws:
            if ch=='\x01':
                if d==0: st=len(out)
                d+=1
            elif ch=='\x02':
                d-=1
                if d==0: sp.append((st,len(out)))
            else: out.append(ch)
        txt=''.join(out)
        SEC[rid]=dict(text=txt,nn=sp,digest='sha256:'+hashlib.sha256(txt.encode()).hexdigest(),length=len(txt))
    check("SR-02","69 requirement labels exist with no duplicates",len(LAB)==69 and len({r for r,_ in LAB})==69,len(LAB))
    src_ids={r for r,_ in LAB}; cid=set(x['id'] for x in reqs)
    check("SR-02b","coverage requirement IDs exactly match the source label set",src_ids==cid,
          f"missing={sorted(src_ids-cid)[:5]} extra={sorted(cid-src_ids)[:5]}")


cov_ids=[r['id'] for r in reqs]
check("SR-03","coverage.yaml contains 69 requirements",len(reqs)==69,len(reqs))
check("SR-03b","coverage.yaml requirement IDs are unique",len(cov_ids)==len(set(cov_ids)),
      [x for x in set(cov_ids) if cov_ids.count(x)>1])
badpar=[o['key'] for r in reqs for o in r['obligations']
        if not o['key'].startswith(r['id']+'.')]
check("SR-03c","obligation keys start with the parent requirement ID plus '.'",not badpar,badpar[:6])
# Suffixes normally use [a-z]; after exhausting a–z, two letters (aa, ab, ...) are allowed.
# A numeric suffix (h1, k2, ...) denotes a detail derived from the same normative sentence.
badsuf=[o['key'] for _,o in obs if not re.fullmatch(r'[a-z]{1,2}[0-9]?',o['key'].rsplit('.',1)[1])]
check("SR-03d","obligation key suffixes match [a-z]{1,2}[0-9]?",not badsuf,badsuf[:6])
check("SR-04","all 69 requirements have at least one obligation",all(r['obligations'] for r in reqs),
      [r['id'] for r in reqs if not r['obligations']])

if SEC:
    bad=[r['id'] for r in reqs if r['source_section_digest']!=SEC.get(r['id'],{}).get('digest')]
    check("SR-05","recorded section digests match values recomputed from the source",not bad,bad[:5])
    badlen=[r['id'] for r in reqs if r['source_section_length']!=SEC.get(r['id'],{}).get('length')]
    check("SR-06","recorded section lengths match",not badlen,badlen[:5])
    badnn=[r['id'] for r in reqs
           if [(s['start'],s['end']) for s in (r['non_normative_spans'] or [])]!=SEC.get(r['id'],{}).get('nn')]
    check("SR-07","recorded non-normative spans match",not badnn,badnn[:5])
    rng=[];dg=[];ovl=[];amb=[]
    for rid,o in obs:
        S=SEC.get(rid)
        if not S: continue
        for c in o['source_clauses']:
            if not(0<=c['start']<c['end']<=S['length']): rng.append(o['key']); continue
            sub=S['text'][c['start']:c['end']]
            if 'sha256:'+hashlib.sha256(sub.encode()).hexdigest()!=c['digest']: dg.append(o['key'])
            if any(not(c['end']<=a or c['start']>=b) for a,b in S['nn']): ovl.append(o['key'])
            n=S['text'].count(sub)
            if n!=c.get('occurrences') : amb.append(f"{o['key']}:recorded={c.get('occurrences')} actual={n}")
    check("SR-08","0 <= start < end <= section length; no empty ranges",not rng,rng[:5])
    check("SR-09","source_clause digests match source substrings",not dg,dg[:5])
    check("SR-10","source_clauses do not overlap non-normative spans",not ovl,ovl[:5])
    check("SR-11","clause occurrence counts match recorded values (detect missed multiple matches)",not amb,amb[:5])
    multi=[f"{o['key']}({c['occurrences']})" for rid,o in obs for c in o['source_clauses'] if c.get('occurrences',1)>1]
    check("SR-12","no clause string occurs multiple times within a section (ambiguous if so)",not multi,multi[:8])
    # Check terms at clause granularity.
    bc=[];bx=[]
    for rid,o in obs:
        cond=o.get('condition') or {}
        S=SEC.get(rid)
        if not S: continue
        clause=' '.join(S['text'][c['start']:c['end']] for c in o['source_clauses'])
        if cond.get('predicate_kind')=='CLAIM_BASED' and not re.search(r'claim',clause,re.I): bc.append(o['key'])
    check("SR-13","CLAIM_BASED conditions attach to clauses containing 'claim'",not bc,bc)

# ---- Structural checks (run without source text) ----
pk={n:d['kind'] for n,d in preds['predicates'].items()}
unk=[o['key'] for _,o in obs if (o.get('condition') or {}).get('predicate') not in list(pk)+[None]]
check("SR-15","condition predicate is defined in predicates.yaml",not unk,unk)
mism=[o['key'] for _,o in obs if o.get('condition') and pk.get(o['condition']['predicate'])!=o['condition']['predicate_kind']]
check("SR-16","condition predicate_kind matches predicates.yaml",not mism,mism)
noobs=[n for n,d in preds['predicates'].items() if d['kind']=='CAPABILITY_BASED' and not d.get('observed')]
check("SR-17","CAPABILITY_BASED predicates have a non-empty observed value",not noobs,noobs)
noexcl=[n for n,d in preds['predicates'].items() if d['kind']=='CLASSIFICATION_BASED' and not d.get('declaration_only_exclusion')]
check("SR-18","CLASSIFICATION_BASED predicates have declaration_only_exclusion",not noexcl,noexcl)
nocfg=[o['key'] for _,o in obs if o['testability']=='CONFIG' and not o.get('configuration_failure_semantics')]
check("SR-19","every CONFIG obligation declares configuration_failure_semantics",not nocfg,nocfg)
badcfg=[o['key'] for _,o in obs if o.get('configuration_failure_semantics') not in (None,'normative_capability','test_precondition')]
check("SR-20","configuration_failure_semantics is one of the two defined values",not badcfg,badcfg)
LV={'MUST','MUST_NOT','REQUIRED','SHOULD','SHOULD_NOT','RECOMMENDED','MAY','OPTIONAL'}
badlv=[o['key'] for _,o in obs if o['level'] not in LV]
check("SR-21","level is an RFC 2119 defined value",not badlv,badlv)
novar=[o['key'] for _,o in obs if o['testability']!='NOT_OBSERVABLE' and not o.get('required_variants')]
check("SR-22","all non-NOT_OBSERVABLE obligations have required_variants",not novar,novar)
# ---- linked_obligations (reference inclusion) ----
# The meaning is defined in docs/03-test-model.md, under the link semantics section.
# This only checks that G2 can mechanically apply the expansion rules.
LINK_KINDS={'inherit_variants'}   # Update docs/03 and g1_author.py together when adding kinds.
LINK_VARIANT_APPLICABILITY={'owner_condition','linked_condition'}
_keys={o['key'] for _,o in obs}
_obl={o['key']:o for _,o in obs}
_badshape=[(o['key'],lk) for _,o in obs for lk in (o.get('linked_obligations') or [])
           if not isinstance(lk,dict) or not lk.get('obligation') or not lk.get('kind') or not lk.get('note_en')]
check("SR-22g-shape","linked_obligations have the shape {obligation, kind, optional variant_applicability, note_en}",not _badshape,_badshape[:5])
def _links(o):
    return [lk for lk in (o.get('linked_obligations') or []) if isinstance(lk,dict) and lk.get('obligation')]
_dang=[(o['key'],lk['obligation']) for _,o in obs for lk in _links(o) if lk['obligation'] not in _keys]
check("SR-22d","linked_obligations targets exist",not _dang,_dang[:5])
_self=[o['key'] for _,o in obs if o['key'] in [lk['obligation'] for lk in _links(o)]]
check("SR-22e","linked_obligations do not self-reference",not _self,_self[:5])
_link={o['key']:[lk['obligation'] for lk in _links(o)] for _,o in obs}
def _cyc():
    for k in _link:
        st=[(k,[k])]
        while st:
            cur,path=st.pop()
            for nx in _link.get(cur,()):
                if nx in path: return path+[nx]
                st.append((nx,path+[nx]))
    return None
_c=_cyc()
check("SR-22f","linked_obligations contain no cycles",_c is None,_c or '')
_badkind=[(o['key'],lk['kind']) for _,o in obs for lk in _links(o) if lk['kind'] not in LINK_KINDS]
check("SR-22g","linked_obligation kinds use the defined vocabulary",not _badkind,_badkind[:5])
_badscope=[(o['key'],lk.get('variant_applicability')) for _,o in obs for lk in _links(o)
           if lk.get('variant_applicability','owner_condition') not in LINK_VARIANT_APPLICABILITY]
check("SR-22j","linked_obligation variant_applicability uses the defined vocabulary",not _badscope,_badscope[:5])
_missing_link_condition=[(o['key'],lk['obligation']) for _,o in obs for lk in _links(o)
                         if lk.get('variant_applicability')=='linked_condition'
                         and not (_obl.get(lk['obligation']) or {}).get('condition')]
check("SR-22k","linked_condition scope points to an obligation with a condition",not _missing_link_condition,_missing_link_condition[:5])
# The transitive expansion must be finite and non-empty. G2 covers_variants uses this set as its denominator.
def _expand(key,depth=0):
    """Build the transitive variant reference set for inherit_variants.
    SR-22f guarantees that there are no cycles.

    ★ Do not raise for missing keys or unknown kinds. If this fails here, the
      SR-22d / SR-22g findings disappear from the report and the validator dies silently.
    """
    o=_obl.get(key)
    if o is None: return set()          # SR-22d reports missing references.
    if depth>4: raise RuntimeError(f"{key}: link depth exceeds 4")
    out={f"{key}#{v['id']}" for v in (o.get('required_variants') or [])}
    for lk in _links(o):
        if lk['kind']=='inherit_variants':
            out |= _expand(lk['obligation'],depth+1)
    return out
_empty=[];_deep=[]
for _,o in obs:
    if not _links(o): continue
    try:
        if not _expand(o['key']): _empty.append(o['key'])
    except RuntimeError as e: _deep.append(str(e))
check("SR-22h","linked_obligation expansion is finite (depth <= 4) and non-empty",not _empty and not _deep,(_empty+_deep)[:5])
# A NOT_OBSERVABLE source (with no variants) adds nothing when expanded; the link is meaningless.
_noop=[(o['key'],lk['obligation']) for _,o in obs for lk in _links(o)
       if lk['kind']=='inherit_variants' and _obl.get(lk['obligation'],{}).get('testability')=='NOT_OBSERVABLE']
check("SR-22i","inherit_variants targets are not NOT_OBSERVABLE",not _noop,_noop[:5])

badv=[o['key'] for _,o in obs
      for v in (o.get('required_variants') or [])
      if not isinstance(v,dict) or not v.get('id') or not v.get('description_en')]
check("SR-22b","required_variants have the shape {id, description_en} and stable IDs",not badv,badv[:5])
_vids=[v['id'] for _,o in obs for v in (o.get('required_variants') or [])]
check("SR-22c","variant IDs are unique across all variants",len(_vids)==len(set(_vids)),
      f"{len(_vids)-len(set(_vids))} duplicates")
nore=[o['key'] for _,o in obs if o['testability']=='NOT_OBSERVABLE' and not o.get('not_observable_reason_en')]
check("SR-23","NOT_OBSERVABLE obligations have a reason",not nore,nore)
norv=[o['key'] for _,o in obs if not o.get('review') or 'state' not in o['review']]
check("SR-24","all obligations have a review block",not norv,norv)
badrv=[o['key'] for _,o in obs
       if not all(o.get('review',{}).get(k) for k in ('source_spec','spec_version','source_selector','source_section_digest'))]
check("SR-25","review contains source_spec / spec_version / source_selector / source_section_digest",not badrv,badrv[:5])
cat_now=X.catalog_digest(specs,preds)
check("SR-25a","coverage.yaml catalog_digest matches current specs.yaml + predicates.yaml values",
      cov.get('catalog_digest')==cat_now, f"recorded={cov.get('catalog_digest')} now={cat_now}")
nod=[o['key'] for _,o in obs if not (o.get('review') or {}).get('obligation_digest')]
check("SR-25b","all obligations have review.obligation_digest",not nod,nod[:5])
staled=[o['key'] for _,o in obs
        if (o.get('review') or {}).get('obligation_digest') and
           (o['review']['obligation_digest']!=X.obligation_digest(o,preds['predicates']))]
check("SR-25c","review.obligation_digest matches the current obligation content (detect changes before approval)",not staled,staled[:6])
selfrev=[o['key'] for _,o in obs if o['review'].get('reviewer') and o['review']['reviewer']==o.get('authored_by')]
check("SR-26","reviewer differs from the author (unset is allowed)",not selfrev,selfrev)
uses=sorted({o['references_spec'].split('#')[0] for _,o in obs if o.get('references_spec')})
missing=[u for u in uses if u not in specs['specs']]
check("SR-27","references_spec is registered in specs.yaml",not missing,missing)
nourl=[k for k,v in specs['specs'].items() if v.get('role')!='referenced-unversioned' and not v.get('url')]
check("SR-28","all versioned specifications have a URL",not nourl,nourl)
dupe=[o['key'] for _,o in obs]
check("SR-29","obligation keys are unique",len(dupe)==len(set(dupe)),len(dupe)-len(set(dupe)))

# ---- Fetch and reconcile referenced specifications ----
used=[] if STRUCT_ONLY else sorted({o['references_spec'].split('#')[0] for _,o in obs if o.get('references_spec')} |
            {ev['spec'] for _,o in obs for ev in (o.get('reference_evidence') or [])})
nodg=[] if STRUCT_ONLY else [k for k,v in specs['specs'].items()
      if v.get('role')!='referenced-unversioned' and not v.get('source_digest')]
if not STRUCT_ONLY:
    check("SR-32","all catalog specifications except unversioned documents have a recorded source_digest",not nodg,nodg)
used_nodg=[] if STRUCT_ONLY else [k for k in used if not specs['specs'].get(k,{}).get('source_digest')
           and specs['specs'].get(k,{}).get('role')!='referenced-unversioned']
if not STRUCT_ONLY:
    check("SR-32b","all specifications referenced by obligations have a source_digest",not used_nodg,used_nodg)

reftext={}; fetch_fail=[]; dg_bad=[]
for k in ([] if STRUCT_ONLY else sorted(specs['specs'])):
    sp=specs['specs'].get(k) or {}
    if not sp.get('url') or not sp.get('source_digest'): continue
    try:
        raw,path=X.fetch(ROOT,k,sp['url'],mode=MODE)
    except Exception as e:
        fetch_fail.append(f'{k}:{type(e).__name__}'); continue
    if raw is None: fetch_fail.append(k); continue
    if X.sha(raw)!=sp['source_digest']: dg_bad.append(f"{k}: {X.sha(raw)} != {sp['source_digest']}")
    else:
        try: reftext[k]=X.normalize(raw,sp['url'])
        except Exception as e: fetch_fail.append(f"{k}:{e}")
if not STRUCT_ONLY: check("SR-33","all catalog specifications can be fetched and match their recorded source_digest",
      not dg_bad and not fetch_fail, f"digest_mismatch={dg_bad[:3]} unavailable={fetch_fail[:3]}")

ev_bad=[]; ev_n=0
for rid,o in ([] if STRUCT_ONLY else obs):
    for ev in o.get('reference_evidence') or []:
        ev_n+=1
        t2=reftext.get(ev['spec'])
        if t2 is None: ev_bad.append(f"{o['key']}: {ev['spec']} not fetched"); continue
        try: sec=X.section(t2,ev['locator'])
        except KeyError as e: ev_bad.append(f"{o['key']}: locator cannot be resolved: {e}"); continue
        if X.sha(sec)!=ev['section_digest']:
            ev_bad.append(f"{o['key']}: section digest mismatch")
if not STRUCT_ONLY: check("SR-34","reference_evidence locators resolve and section digests match",not ev_bad,
      f"n={ev_n} bad={ev_bad[:4]}")

# ---- SR-14: verify the existence of exclusion clauses ----
# CLASSIFICATION_BASED conditions require an explicit exclusion in the source text.
# Keep that exclusion clause verbatim and verify it exists in an IIP requirement
# section or a referenced specification section.
# (The previous version only checked for 'does not apply' in the requirement section,
# rejecting obligations whose exclusion was in a referenced specification and accepting unrelated text.)
cls=[(rid,o) for rid,o in obs if (o.get('condition') or {}).get('predicate_kind')=='CLASSIFICATION_BASED']
noexcl=[o['key'] for _,o in cls if not o.get('exclusion_clause_en')]
strayexcl=[o['key'] for _,o in obs
           if o.get('exclusion_clause_en')
           and (o.get('condition') or {}).get('predicate_kind')!='CLASSIFICATION_BASED']
check("SR-14a","CLASSIFICATION_BASED obligations have exclusion_clause_en and other obligations do not",
      not noexcl and not strayexcl,(noexcl+strayexcl)[:5])
if not STRUCT_ONLY:
    bx=[]
    for rid,o in cls:
        ex=o.get('exclusion_clause_en')
        if not ex: continue
        hay=[]
        S=SEC.get(rid)
        if S: hay.append(S['text'])
        for ev in o.get('reference_evidence') or []:
            t2=reftext.get(ev['spec'])
            if t2 is None: continue
            try: hay.append(X.section(t2,ev['locator']))
            except KeyError: pass
        if not any(ex in h for h in hay):
            bx.append(f"{o['key']}: exclusion clause exists in neither the IIP nor referenced section")
    check("SR-14","exclusion_clause_en exists verbatim in an IIP or referenced section",not bx,bx[:5])

# Derive whether reference evidence is required from the obligation declaration
# (reference_derivation); do not hard-code it.
nodecl=[o['key'] for _,o in obs if o.get('references_spec') and o.get('reference_derivation') is None]
check("SR-35","all obligations with references_spec explicitly declare reference_derivation",not nodecl,nodecl[:8])
noev=[o['key'] for _,o in obs if o.get('reference_derivation') is True and not o.get('reference_evidence')]
check("SR-35b","reference_derivation: true obligations have reference_evidence",not noev,noev)
nonote=[o['key'] for _,o in obs if o.get('reference_derivation') is False and not o.get('reference_derivation_note')]
check("SR-35d","reference_derivation: false obligations have a reason (reference_derivation_note)",not nonote,nonote[:6])
orph=[o['key'] for _,o in obs if o.get('reference_evidence') and o.get('reference_derivation') is not True]
check("SR-35c","obligations with reference_evidence have reference_derivation: true",not orph,orph)

# ---- G1 completion conditions ----
opens=[o['key'] for _,o in obs if any(k.startswith('open_question_') for k in o)]
check("SR-30","no unresolved open questions remain (G1 completion condition)",not opens,opens)

# An obligation claiming APPROVED must have complete approval evidence;
# changing state alone is not sufficient.
secdg={r['id']:r['source_section_digest'] for r in reqs}
bad_appr=[]
for rid,o in obs:
    rv=o.get('review') or {}
    if rv.get('state')!='APPROVED': continue
    k=o['key']
    if not rv.get('reviewer'):      bad_appr.append(f"{k}: reviewer is unset")
    if not rv.get('approved_at'):   bad_appr.append(f"{k}: approved_at is unset")
    if rv.get('reviewer') and rv.get('reviewer')==o.get('authored_by'):
        bad_appr.append(f"{k}: reviewer==authored_by")
    if rv.get('source_spec')!=cov['spec']:           bad_appr.append(f"{k}: source_spec mismatch")
    if str(rv.get('spec_version'))!=str(cov['spec_version']): bad_appr.append(f"{k}: spec_version mismatch")
    if rv.get('source_selector') not in (rid,'#'+rid): bad_appr.append(f"{k}: source_selector mismatch")
    if rv.get('source_section_digest')!=secdg.get(rid):
        bad_appr.append(f"{k}: approval-time section digest differs from the current value (source may have changed)")
    if rv.get('obligation_digest')!=X.obligation_digest(o,preds['predicates']):
        bad_appr.append(f"{k}: approval-time obligation digest differs from the current value (obligation changed)")
noauth=[o['key'] for _,o in obs if not o.get('authored_by')]
check("SR-25d","all obligations have authored_by (needed to compare reviewer and author)",not noauth,noauth[:5])

# ============================================================================
# Approval (G1b) validation
#
# Design:
#   - Approval target = tests/{coverage,specs,predicates}.yaml at a commit C.
#   - Approval record = tests/approvals/g1.yaml, placed outside C (in a later commit).
#     (Putting the record in C would make C self-referential when the record is appended.)
#   - Approval authenticity = established only by a signed git object (commit / tag).
#     The reviewer string in YAML is self-reported and is not sufficient by itself.
# ============================================================================
import subprocess,datetime as _dt,re as _re

def _git(*a,binary=False):
    try:
        return subprocess.run(['git','-C',ROOT]+list(a),
                              capture_output=True,text=not binary,timeout=20)
    except Exception:
        return None



def _iso_full(v):
    """Parse the entire string as ISO-8601 and allow only timezone-aware datetimes."""
    t=str(v)
    try:
        d=_dt.datetime.fromisoformat(t.replace('Z','+00:00'))
    except Exception:
        return False
    return d.tzinfo is not None

# Files protected by approval, including this validator (to detect validator weakening).
PROTECTED_PATHS=('tests/coverage.yaml','tests/specs.yaml','tests/predicates.yaml',
                 'tests/approvals/g1.yaml','tools/g1_validate.py','tools/g1_extract.py',
                 'tools/g1_migration_validate.py','tools/g1_schema_validate.py',
                 'tools/g1_language_check.py','tools/g1-semantic-exceptions.yaml',
                 'schema/g1-coverage-v2.json','schema/g1-predicates-v2.json',
                 'schema/g1-specs-v2.json','schema/g1-variant-map-v1.json',
                 'schema/g1-semantic-exceptions-v1.json')
APPROVAL_REL='tests/approvals/g1.yaml' 
APPROVAL_PATH=os.path.join(ROOT,APPROVAL_REL)
appr=None; appr_src_problems=[]; _sig_info=None
if os.path.exists(APPROVAL_PATH):
    # ★ The approval record of record is the content in the signed commit
    #    containing it, not the working-tree content. Trusting the working tree
    #    would allow content changes while retaining the signed commit reference.
    try: appr_peek=yaml.safe_load(open(APPROVAL_PATH,encoding='utf-8'))
    except Exception: appr_peek=None
    _lg=_git('log','-1','--format=%H','--',APPROVAL_REL)
    _sig_commit=_lg.stdout.strip() if _lg and _lg.returncode==0 else ''
    if not _sig_commit:
        appr_src_problems.append(f"{APPROVAL_REL} is not committed to git (approval must be committed)")
    else:
        _kind=((appr_peek or {}).get('evidence') or {}).get('kind')
        if _kind=='signed-tag':
            _tag=((appr_peek or {}).get('evidence') or {}).get('tag')
            if not _tag:
                appr_src_problems.append("evidence.tag is required when evidence.kind=signed-tag")
                _v=None
            else:
                _v=_git('verify-tag',str(_tag))
                _rt=_git('rev-list','-n','1',str(_tag))
                if not _rt or _rt.returncode!=0 or _rt.stdout.strip()!=_sig_commit:
                    appr_src_problems.append(f"tag {_tag} does not point to commit {_sig_commit[:12]} containing the approval record")
        else:
            _v=_git('verify-commit',_sig_commit)
        if _kind=='signed-tag':
            _tg=((appr_peek or {}).get('evidence') or {}).get('tag')
            _oid=_git('rev-parse',f'{_tg}^{{}}') if _tg else None
            _tobj=_git('rev-parse',str(_tg)) if _tg else None
            _who=_git('for-each-ref','--format=%(taggername) %(taggeremail)',f'refs/tags/{_tg}') if _tg else None
            _raw=_git('verify-tag','--raw',str(_tg)) if _tg else None
            _sig_info=dict(kind='signed-tag',tag=_tg,
                           tag_object=(_tobj.stdout.strip() if _tobj and _tobj.returncode==0 else None),
                           tagged_commit=(_oid.stdout.strip() if _oid and _oid.returncode==0 else None),
                           tagger=(_who.stdout.strip() if _who and _who.returncode==0 else None),
                           verify_raw=((_raw.stderr or _raw.stdout).strip().splitlines()[:3] if _raw else None))
        else:
            _si=_git('log','-1','--format=%GS|%GK|%GT',_sig_commit)
            if _si and _si.returncode==0 and _si.stdout.strip():
                _p=_si.stdout.strip().split('|')
                _sig_info=dict(kind='signed-commit',commit=_sig_commit,
                               signer=_p[0] if len(_p)>0 else None,
                               key=_p[1] if len(_p)>1 else None,
                               trust=_p[2] if len(_p)>2 else None)
        if not _v or _v.returncode!=0:
            appr_src_problems.append(f"approval record signature verification failed ({_kind or 'signed-commit'} / commit {_sig_commit[:12]})")
        else:
            _blob=_git('show',f'{_sig_commit}:{APPROVAL_REL}',binary=True)
            if not _blob or _blob.returncode!=0:
                appr_src_problems.append("cannot read the approval record from the signed commit")
            else:
                appr=yaml.safe_load(_blob.stdout.decode('utf-8'))
                appr['_signed_commit']=_sig_commit
                # ★ Approval protects more than the approval record.
                #   Compare the signed commit A's tree with every current protected file.
                #   Otherwise coverage could be changed after A and its digest recomputed.
                for _rel in PROTECTED_PATHS:
                    _cur=os.path.join(ROOT,_rel)
                    _b=_git('show',f'{_sig_commit}:{_rel}',binary=True)
                    if not _b or _b.returncode!=0:
                        appr_src_problems.append(f"{_rel} is absent from the signed commit"); continue
                    if not os.path.exists(_cur):
                        appr_src_problems.append(f"{_rel} is absent from the working tree"); continue
                    if X.sha(open(_cur,'rb').read())!=X.sha(_b.stdout):
                        appr_src_problems.append(f"{_rel} differs from the signed approval commit (changed after approval)")
                # Also detect additions and deletions (the file sets under tests/ and tools/ must match).
                for _dir in ('tests','tools'):
                    _ls=_git('ls-tree','-r','--name-only',_sig_commit,_dir)
                    if not _ls or _ls.returncode!=0: continue
                    _a=set(_ls.stdout.split())
                    _n=set()
                    for _dp,_dn,_fn in os.walk(os.path.join(ROOT,_dir)):
                        _dn[:]=[d for d in _dn if d!='__pycache__']
                        for _f in _fn:
                            if _f.endswith('.pyc'): continue
                            _n.add(os.path.relpath(os.path.join(_dp,_f),ROOT))
                    # Exclude gitignored files (such as tools/g1_authoring.py) from the set.
                    if _n:
                        _ci=subprocess.run(['git','-C',ROOT,'check-ignore','--stdin'],
                                           input='\n'.join(sorted(_n)),capture_output=True,text=True,timeout=30)
                        _n-=set(_ci.stdout.split())
                    if _a!=_n:
                        appr_src_problems.append(
                            f"file set under {_dir}/ differs from the signed commit"
                            f" (added {sorted(_n-_a)[:3]} / deleted {sorted(_a-_n)[:3]})")

# The coverage.yaml state is an authoring record, not the approval record of record.
state_claims=[o['key'] for _,o in obs if (o.get('review') or {}).get('state')!='PENDING_REVIEW']

approved_keys=set(); appr_problems=[]; appr_entries={}; signers=set()
if appr is None:
    if state_claims:
        appr_problems.append(f"coverage.yaml claims APPROVED without tests/approvals/g1.yaml ({len(state_claims)} obligations)")
else:
    tc=str(appr.get('target_commit') or '')
    if not _re.fullmatch(r'[0-9a-f]{40}',tc):
        appr_problems.append("target_commit must be a complete 40-character SHA-1 (abbreviated SHAs are not allowed)")
    else:
        r=_git('rev-parse','--verify','--quiet',tc+'^{commit}')
        if not r or r.returncode!=0 or r.stdout.strip()!=tc:
            appr_problems.append(f"target_commit {tc[:12]} does not exist in git or cannot be resolved exactly")
        else:
            # Read approval targets from the target commit and compare their digests.
            for rel in ('tests/coverage.yaml','tests/specs.yaml','tests/predicates.yaml'):
                r2=_git('show',f'{tc}:{rel}',binary=True)
                if not r2 or r2.returncode!=0:
                    appr_problems.append(f"{rel} is absent from the target commit"); continue
                want=(appr.get('artifact_digests') or {}).get(rel)
                got=X.sha(r2.stdout)
                if want!=got:
                    appr_problems.append(f"{rel}: approval-record digest differs from target commit content")
            # Recompute obligation digests from the target commit's coverage.
            r3=_git('show',f'{tc}:tests/coverage.yaml',binary=True)
            r4=_git('show',f'{tc}:tests/predicates.yaml',binary=True)
            if r3 and r3.returncode==0 and r4 and r4.returncode==0:
                cdoc=yaml.safe_load(r3.stdout.decode('utf-8'))
                cpred=yaml.safe_load(r4.stdout.decode('utf-8'))['predicates']
                target_obs={o['key']:o for rq in cdoc['requirements'] for o in rq['obligations']}
                target_auth={k:v.get('authored_by') for k,v in target_obs.items()}
                for e in (appr.get('approvals') or []):
                    k=e.get('obligation')
                    appr_entries[k]=e
                    if k not in target_obs: appr_problems.append(f"{k}: obligation is absent from the target commit"); continue
                    if e.get('obligation_digest')!=X.obligation_digest(target_obs[k],cpred):
                        appr_problems.append(f"{k}: approval digest differs from target commit content"); continue
                    if not e.get('reviewer'): appr_problems.append(f"{k}: reviewer is unset"); continue
                    if e['reviewer']==target_auth.get(k): appr_problems.append(f"{k}: reviewer==authored_by"); continue
                    if not _iso_full(e.get('approved_at')):
                        appr_problems.append(f"{k}: approved_at is not a timezone-aware ISO-8601 value"); continue
                    approved_keys.add(k)
                missing=[k for k in target_obs if k not in appr_entries]
                if missing: appr_problems.append(f"{len(missing)} obligations are not approved (examples {missing[:3]})")
    # External evidence: accept only signed git objects.
    ev=appr.get('evidence') or {}
    if ev.get('kind') not in ('signed-commit','signed-tag'):
        appr_problems.append("evidence.kind is required and must be signed-commit or signed-tag")
    if not (ev.get('reviewers') or []):
        appr_problems.append("evidence.reviewers is required and must be non-empty (to compare with per-obligation reviewers)")
    if not ev.get('evidence_url'):
        appr_problems.append("evidence.evidence_url is required (PR or review-record URL)")
    # Signature verification has already been performed against the commit
    # containing the approval record (appr_src_problems).
    # ★ Do not add fields such as evidence.ref. Recording the SHA of a commit
    #   containing the record is self-referential; the SHA changes on re-signing.
    #   A signed commit is uniquely identified by `git log -1 -- <path>`.
    if 'ref' in ev:
        appr_problems.append("do not use evidence.ref (it is self-referential); remove the field")
    if ev.get('evidence_url') and not _re.match(r'https://[^\s]+\.[^\s]+',str(ev['evidence_url'])):
        appr_problems.append("evidence_url is not an HTTPS URL")
    # ★ Bind the signer principal to the reviewer.
    #   The YAML reviewer is self-reported; an allowed-key holder could otherwise
    #   enter a fictitious name and bypass reviewer != authored_by.
    signers=set()
    if _sig_info:
        if _sig_info.get('kind')=='signed-tag':
            _t=_sig_info.get('tagger') or ''
            _m=_re.search(r'<([^>]+)>',_t)
            if _m: signers.add(_m.group(1).strip())
            if _t: signers.add(_t.split('<')[0].strip())
        else:
            if _sig_info.get('signer'): signers.add(str(_sig_info['signer']).strip())
    signers={x for x in signers if x}
    # Externally fixed principal -> reviewer-ID mapping (provided by CI;
    # use the principal itself when absent).
    _map={}
    for _pair in (os.environ.get('G1_SIGNER_MAP') or '').split(','):
        if '=' in _pair:
            _k,_v=_pair.split('=',1); _map[_k.strip()]=_v.strip()
    mapped={_map.get(x,x) for x in signers}
    if not signers:
        appr_problems.append("could not obtain the signer principal (cannot bind it to a reviewer)")
    else:
        unbound=sorted({e.get('reviewer') for e in (appr.get('approvals') or []) if e.get('reviewer')} - mapped)
        if unbound:
            appr_problems.append(
                f"reviewer does not match the signer principal: {unbound[:3]} (signers={sorted(mapped)}). "
                f"Allowing multiple reviewers requires a signed record for each reviewer")

    # Each per-obligation reviewer must be included in evidence.reviewers.
    allowed=set(ev.get('reviewers') or [])
    bad_rv=sorted({e.get('reviewer') for e in (appr.get('approvals') or [])} - allowed - {None})
    if bad_rv: appr_problems.append(f"reviewers missing from evidence.reviewers: {bad_rv[:3]}")

_ut=_git('status','--porcelain','--untracked-files=all','tools')
_extra=[l[3:] for l in (_ut.stdout.splitlines() if _ut and _ut.returncode==0 else []) if l[3:].endswith('.py')]
check("SR-40","tools/ has no untracked or uncommitted Python modules (prevent shadow imports)",
      not _extra,_extra[:5])

check("SR-38","approval is bound to a signed record outside the target commit",
      not appr_problems and not appr_src_problems,(appr_src_problems+appr_problems)[:6])

pending=[o['key'] for _,o in obs if o['key'] not in approved_keys]
check("SR-31","all obligations are approved (G1 approval condition)",not pending,f"{len(pending)}/{len(obs)} unapproved")

# ---- SR-41: detect hard-coded totals ----
# Hard-coding "N obligations / N requirements / N specifications / N predicates"
# in docs leaves stale values when obligations are added (four files once remained
# at 133 / 132). Enforce that totals appear only through <!--g1:KEY-->...<!--/g1--> markers.
#   Exception 1: 04-requirement-coverage.md is fully generated (checked by g1_docgen.py --check).
#   Exception 2: 11-review-log.md is a review record and retains historical values (also excluded from docgen).
#   Exception 3: the line contains <!--g1-literal--> (a fictitious explanatory number).
_STAT_SKIP={'04-requirement-coverage.md','11-review-log.md'}
_MARK_SPAN=re.compile(r'<!--g1:[a-z_]+-->.*?<!--/g1-->',re.S)
_BARE=re.compile(r'(?<![\d.])(\d+)\s*(obligations?|requirements?|specifications?|predicates?|variants?)',re.I)
_scan=[]
for _d,_fs in (('docs',sorted(os.listdir(os.path.join(ROOT,'docs')))),
               ('tools',['ci-stages.md','README.md']),('.',['AGENTS.md'])):
    for _f in _fs:
        if not _f.endswith('.md') or _f in _STAT_SKIP: continue
        _p=os.path.join(ROOT,_d,_f)
        if os.path.exists(_p): _scan.append((os.path.relpath(_p,ROOT),_p))
_hard=[]
for _rel,_p in _scan:
    for _n,_line in enumerate(open(_p,encoding='utf-8').read().split('\n'),1):
        if '<!--g1-literal-->' in _line: continue
        for _m in _BARE.finditer(_MARK_SPAN.sub('',_line)):
            if _m.group(1)=='1': continue     # "1 requirement" and "1 obligation" explain structure, not totals.
            _hard.append(f"{_rel}:{_n}: {_m.group(0)}")
check("SR-41","totals are not hard-coded in prose (use <!--g1:KEY--> markers)",
      not _hard,_hard[:8])

check("SR-39","coverage.yaml g1_state remains its authoring value (PENDING_REVIEW)",
      cov.get('g1_state')=='PENDING_REVIEW',
      f"g1_state={cov.get('g1_state')} (do not edit coverage.yaml for approval; "
      f"completion is derived from tests/approvals/g1.yaml)")

npass=sum(1 for c in R if c['result']=='PASS'); nfail=len(R)-npass
# Unapproved or unresolved items indicate incomplete G1 and are separate from
# whether authoring-phase artifacts may be submitted.
blocking=[c for c in R if c['result']=='FAIL' and c['id'] not in ('SR-30','SR-31')]
g1_ready = (not blocking) and (not opens) and (not pending)
report=dict(task=":specReconcile",run_id=str(uuid.uuid4()),executed_at=NOW,
  validator="tools/g1_validate.py (independent of generation; writes no values back)",
  provenance=dict(
      repo_root=ROOT,
      validator_source=os.environ.get('G1_VALIDATOR_SOURCE'),
      validator_source_kind=os.environ.get('G1_VALIDATOR_SOURCE_KIND'),
      runner_source=os.environ.get('G1_RUNNER_SOURCE'),
      note="unless validator_source_kind is external-pin, the validator source is the "
           "target_commit referenced by the approval record. CI must pin G1_VALIDATOR_COMMIT externally"),
  mode=("structural-only" if STRUCT_ONLY else ("offline" if OFFLINE else "network")),
  source=dict(spec=cov['spec'],version=cov['spec_version'],url=primary['url'],
              recorded_digest=primary.get('source_digest'),cache="build/spec-cache/ (gitignored)"),
  totals=dict(requirements=len(reqs),obligations=len(obs),checks=len(R),passed=npass,failed=nfail,
              blocking_failures=len(blocking)),
  g1_approval=(dict(
      target_commit=(appr or {}).get('target_commit'),
      approval_commit=(appr or {}).get('_signed_commit'),
      signature=_sig_info,
      artifact_digests=(appr or {}).get('artifact_digests'),
      protected_file_digests={r:(X.sha(open(os.path.join(ROOT,r),'rb').read())
                                 if os.path.exists(os.path.join(ROOT,r)) else None)
                              for r in PROTECTED_PATHS},
      reviewers=sorted({e.get('reviewer') for e in ((appr or {}).get('approvals') or []) if e.get('reviewer')}),
      signer_principals=sorted(signers) if appr else None,
      signer_map_applied=bool(os.environ.get('G1_SIGNER_MAP')),
      approved_obligations=len(approved_keys)) if appr else None),
  g1=dict(state=('APPROVED' if g1_ready else 'PENDING_REVIEW'),  # Derived value, not coverage.yaml content.
          authored_state=cov.get('g1_state'),open_questions=opens,unapproved=len(pending),
          blocking_failures=[c['id'] for c in blocking],
          complete=bool(g1_ready),
          complete_formula="no blocking failures AND no open questions AND all obligations approved via tests/approvals/g1.yaml (do not edit coverage.yaml for approval)"),
  checks=R,
  note="SR-30 / SR-31 are G1 completion conditions and may remain FAIL during the authoring phase. "
       "Other FAIL results indicate artifact defects.")
os.makedirs(BUILD,exist_ok=True)
json.dump(report,open(os.path.join(BUILD,'spec-reconcile-report.json'),'w',encoding='utf-8'),ensure_ascii=False,indent=2)
print(f"{npass}/{len(R)} PASS  (blocking failures: {len(blocking)})")
for c in R:
    if c['result']=='FAIL': print(("  BLOCK " if c in blocking else "  g1-pending ")+c['id'],c['description'],'|',c['detail'][:90])
sys.exit(1 if blocking else 0)
