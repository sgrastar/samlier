#!/usr/bin/env python3
"""g1_validate.py — G1 成果物の独立検証（:specReconcile）

★ 生成処理から独立している。コミット済みの成果物を「読み込んで」照合するだけで、
   一切の値を書き戻さない。原文は取得（またはキャッシュ）したものを使い、
   ダイジェストは specs.yaml に記録済みの値と比較する。

  使い方:  python3 tools/g1_validate.py [--offline]
  依存  :  PyYAML
  出力  :  build/spec-reconcile-report.json  終了コード 0=PASS / 1=FAIL
"""
import os,re,sys,json,html,hashlib,unicodedata,datetime,uuid,urllib.request
# ★ tools/ を sys.path に入れない。未追跡の tools/yaml.py 等で
#   標準/サードパーティモジュールを shadow され、署名検証より先に任意コードが走る。
try:
    import yaml
except ImportError:
    sys.exit("PyYAML が必要です:  .venv/bin/pip install -r tools/requirements.txt")
# g1_extract は「この validator と同じディレクトリのファイル」を明示パスで読み込む
import importlib.util as _ilu
_here=os.path.dirname(os.path.abspath(__file__))
_spec=_ilu.spec_from_file_location('g1_extract_local',os.path.join(_here,'g1_extract.py'))
X=_ilu.module_from_spec(_spec); _spec.loader.exec_module(X)

ROOT=os.environ.get('G1_REPO_ROOT') or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TESTS=os.path.join(ROOT,'tests'); BUILD=os.path.join(ROOT,'build')
CACHE=os.path.join(BUILD,'spec-cache'); os.makedirs(CACHE,exist_ok=True)
OFFLINE='--offline' in sys.argv
MODE='offline' if OFFLINE else 'network'
NOW=datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()

R=[]
def check(cid,desc,passed,detail=""):
    R.append(dict(id=cid,description=desc,result="PASS" if passed else "FAIL",detail=str(detail)[:400]))
    return passed

def load(name):
    with open(os.path.join(TESTS,name),encoding='utf-8') as f: return yaml.safe_load(f)
specs=load('specs.yaml'); cov=load('coverage.yaml'); preds=load('predicates.yaml')

# ---- 原文の取得（validator 自身は digest を書かない） ----
primary=specs['specs'][cov['spec']]
try:
    raw,_=X.fetch(ROOT,cov['spec'],primary['url'],mode=MODE)
except Exception as e:
    raw=None; check("SR-00","primary spec を取得できる",False,f"{type(e).__name__}: {e}")
if raw is None:
    check("SR-01","原文を取得できる（--offline かつキャッシュなし）",False,"no source"); 
else:
    got='sha256:'+hashlib.sha256(raw).hexdigest()
    check("SR-01","取得した原文のダイジェストが specs.yaml の記録値と一致する",
          got==primary.get('source_digest'), f"fetched={got} recorded={primary.get('source_digest')}")
    check("SR-01b","coverage.yaml の source_digest が specs.yaml と一致する",
          cov.get('source_digest')==primary.get('source_digest'), cov.get('source_digest'))

reqs=cov['requirements']
obs=[(r['id'],o) for r in reqs for o in r['obligations']]

# ---- 正規化（coverage.yaml が宣言する手順をそのまま再現する） ----
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
    check("SR-02","要件ラベルが 69 個、重複なし",len(LAB)==69 and len({r for r,_ in LAB})==69,len(LAB))
    src_ids={r for r,_ in LAB}; cid=set(x['id'] for x in reqs)
    check("SR-02b","coverage の要件 ID 集合が原文のラベル集合と完全一致する",src_ids==cid,
          f"missing={sorted(src_ids-cid)[:5]} extra={sorted(cid-src_ids)[:5]}")


cov_ids=[r['id'] for r in reqs]
check("SR-03","coverage.yaml の要件数が 69",len(reqs)==69,len(reqs))
check("SR-03b","coverage.yaml の要件 ID が一意",len(cov_ids)==len(set(cov_ids)),
      [x for x in set(cov_ids) if cov_ids.count(x)>1])
badpar=[o['key'] for r in reqs for o in r['obligations']
        if not o['key'].startswith(r['id']+'.')]
check("SR-03c","obligation key が親要件 ID + '.' で始まる",not badpar,badpar[:6])
badsuf=[o['key'] for _,o in obs if not re.fullmatch(r'[a-z][0-9]?',o['key'].rsplit('.',1)[1])]
check("SR-03d","obligation key の suffix が [a-z][0-9]? の形式",not badsuf,badsuf[:6])
check("SR-04","全 69 要件に 1 件以上の obligation がある",all(r['obligations'] for r in reqs),
      [r['id'] for r in reqs if not r['obligations']])

if SEC:
    bad=[r['id'] for r in reqs if r['source_section_digest']!=SEC.get(r['id'],{}).get('digest')]
    check("SR-05","記録された節ダイジェストが原文から再計算した値と一致する",not bad,bad[:5])
    badlen=[r['id'] for r in reqs if r['source_section_length']!=SEC.get(r['id'],{}).get('length')]
    check("SR-06","記録された節長が一致する",not badlen,badlen[:5])
    badnn=[r['id'] for r in reqs
           if [(s['start'],s['end']) for s in (r['non_normative_spans'] or [])]!=SEC.get(r['id'],{}).get('nn')]
    check("SR-07","記録された非規範スパンが一致する",not badnn,badnn[:5])
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
    check("SR-08","0 <= start < end <= 節長 / 空範囲なし",not rng,rng[:5])
    check("SR-09","source_clauses の digest が原文の部分文字列と一致する",not dg,dg[:5])
    check("SR-10","source_clauses が非規範スパンと重ならない",not ovl,ovl[:5])
    check("SR-11","句の出現回数が記録値と一致する（複数一致の見落としを検出）",not amb,amb[:5])
    multi=[f"{o['key']}({c['occurrences']})" for rid,o in obs for c in o['source_clauses'] if c.get('occurrences',1)>1]
    check("SR-12","同一文字列が節内に複数現れる句がない（あれば曖昧）",not multi,multi[:8])
    # 語の検査は句単位
    bc=[];bx=[]
    for rid,o in obs:
        cond=o.get('condition') or {}
        S=SEC.get(rid)
        if not S: continue
        clause=' '.join(S['text'][c['start']:c['end']] for c in o['source_clauses'])
        if cond.get('predicate_kind')=='CLAIM_BASED' and not re.search(r'claim',clause,re.I): bc.append(o['key'])
        if cond.get('predicate_kind')=='CLASSIFICATION_BASED' and 'does not apply' not in S['text']: bx.append(o['key'])
    check("SR-13","CLAIM_BASED の条件が『claim』を含む句に紐づく",not bc,bc)
    check("SR-14","CLASSIFICATION_BASED の義務の要件節に適用除外文が実在する",not bx,bx)

# ---- 構造検査（原文なしでも動く） ----
pk={n:d['kind'] for n,d in preds['predicates'].items()}
unk=[o['key'] for _,o in obs if (o.get('condition') or {}).get('predicate') not in list(pk)+[None]]
check("SR-15","condition の predicate が predicates.yaml に定義済み",not unk,unk)
mism=[o['key'] for _,o in obs if o.get('condition') and pk.get(o['condition']['predicate'])!=o['condition']['predicate_kind']]
check("SR-16","condition の predicate_kind が predicates.yaml と一致",not mism,mism)
noobs=[n for n,d in preds['predicates'].items() if d['kind']=='CAPABILITY_BASED' and not d.get('observed')]
check("SR-17","CAPABILITY_BASED の述語が空でない observed を持つ",not noobs,noobs)
noexcl=[n for n,d in preds['predicates'].items() if d['kind']=='CLASSIFICATION_BASED' and not d.get('declaration_only_exclusion')]
check("SR-18","CLASSIFICATION_BASED の述語が declaration_only_exclusion を持つ",not noexcl,noexcl)
nocfg=[o['key'] for _,o in obs if o['testability']=='CONFIG' and not o.get('configuration_failure_semantics')]
check("SR-19","CONFIG の全 obligation が configuration_failure_semantics を明示している",not nocfg,nocfg)
badcfg=[o['key'] for _,o in obs if o.get('configuration_failure_semantics') not in (None,'normative_capability','test_precondition')]
check("SR-20","configuration_failure_semantics の値が既定の 2 値のいずれか",not badcfg,badcfg)
LV={'MUST','MUST_NOT','REQUIRED','SHOULD','SHOULD_NOT','RECOMMENDED','MAY','OPTIONAL'}
badlv=[o['key'] for _,o in obs if o['level'] not in LV]
check("SR-21","level が RFC2119 の既定値",not badlv,badlv)
novar=[o['key'] for _,o in obs if o['testability']!='NOT_OBSERVABLE' and not o.get('required_variants')]
check("SR-22","NOT_OBSERVABLE 以外の全 obligation に required_variants がある",not novar,novar)
nore=[o['key'] for _,o in obs if o['testability']=='NOT_OBSERVABLE' and not o.get('not_observable_reason_en')]
check("SR-23","NOT_OBSERVABLE の obligation に理由文がある",not nore,nore)
norv=[o['key'] for _,o in obs if not o.get('review') or 'state' not in o['review']]
check("SR-24","全 obligation に review ブロックがある",not norv,norv)
badrv=[o['key'] for _,o in obs
       if not all(o.get('review',{}).get(k) for k in ('source_spec','spec_version','source_selector','source_section_digest'))]
check("SR-25","review に source_spec / spec_version / source_selector / source_section_digest がある",not badrv,badrv[:5])
cat_now=X.catalog_digest(specs,preds)
check("SR-25a","coverage.yaml の catalog_digest が specs.yaml + predicates.yaml の現在値と一致する",
      cov.get('catalog_digest')==cat_now, f"recorded={cov.get('catalog_digest')} now={cat_now}")
nod=[o['key'] for _,o in obs if not (o.get('review') or {}).get('obligation_digest')]
check("SR-25b","全 obligation に review.obligation_digest がある",not nod,nod[:5])
staled=[o['key'] for _,o in obs
        if (o.get('review') or {}).get('obligation_digest') and
           (o['review']['obligation_digest']!=X.obligation_digest(o,preds['predicates']))]
check("SR-25c","review.obligation_digest が現在の義務内容と一致する（承認前でも改変を検出）",not staled,staled[:6])
selfrev=[o['key'] for _,o in obs if o['review'].get('reviewer') and o['review']['reviewer']==o.get('authored_by')]
check("SR-26","reviewer が作成者と異なる（未設定なら空で通過）",not selfrev,selfrev)
uses=sorted({o['references_spec'].split('#')[0] for _,o in obs if o.get('references_spec')})
missing=[u for u in uses if u not in specs['specs']]
check("SR-27","references_spec が specs.yaml に登録済み",not missing,missing)
nourl=[k for k,v in specs['specs'].items() if v.get('role')!='referenced-unversioned' and not v.get('url')]
check("SR-28","版のある全仕様に URL がある",not nourl,nourl)
dupe=[o['key'] for _,o in obs]
check("SR-29","obligation key が一意",len(dupe)==len(set(dupe)),len(dupe)-len(set(dupe)))

# ---- 参照仕様の取得と照合 ----
used=sorted({o['references_spec'].split('#')[0] for _,o in obs if o.get('references_spec')} |
            {ev['spec'] for _,o in obs for ev in (o.get('reference_evidence') or [])})
nodg=[k for k,v in specs['specs'].items()
      if v.get('role')!='referenced-unversioned' and not v.get('source_digest')]
check("SR-32","カタログの全仕様（版なし文書を除く）に source_digest が記録されている",not nodg,nodg)
used_nodg=[k for k in used if not specs['specs'].get(k,{}).get('source_digest')
           and specs['specs'].get(k,{}).get('role')!='referenced-unversioned']
check("SR-32b","義務が参照する全仕様に source_digest がある",not used_nodg,used_nodg)

reftext={}; fetch_fail=[]; dg_bad=[]
for k in sorted(specs['specs']):
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
check("SR-33","カタログの全仕様を取得でき、記録された source_digest と一致する",
      not dg_bad and not fetch_fail, f"digest_mismatch={dg_bad[:3]} unavailable={fetch_fail[:3]}")

ev_bad=[]; ev_n=0
for rid,o in obs:
    for ev in o.get('reference_evidence') or []:
        ev_n+=1
        t2=reftext.get(ev['spec'])
        if t2 is None: ev_bad.append(f"{o['key']}: {ev['spec']} 未取得"); continue
        try: sec=X.section(t2,ev['locator'])
        except KeyError as e: ev_bad.append(f"{o['key']}: locator 解決不可 {e}"); continue
        if X.sha(sec)!=ev['section_digest']:
            ev_bad.append(f"{o['key']}: section digest 不一致")
check("SR-34","reference_evidence の locator が解決でき、節ダイジェストが一致する",not ev_bad,
      f"n={ev_n} bad={ev_bad[:4]}")

# 参照根拠の要否は義務側の宣言（reference_derivation）から導く。ハードコードしない。
nodecl=[o['key'] for _,o in obs if o.get('references_spec') and o.get('reference_derivation') is None]
check("SR-35","references_spec を持つ全義務が reference_derivation を明示している",not nodecl,nodecl[:8])
noev=[o['key'] for _,o in obs if o.get('reference_derivation') is True and not o.get('reference_evidence')]
check("SR-35b","reference_derivation: true の義務に reference_evidence がある",not noev,noev)
nonote=[o['key'] for _,o in obs if o.get('reference_derivation') is False and not o.get('reference_derivation_note')]
check("SR-35d","reference_derivation: false の義務に理由（reference_derivation_note）がある",not nonote,nonote[:6])
orph=[o['key'] for _,o in obs if o.get('reference_evidence') and o.get('reference_derivation') is not True]
check("SR-35c","reference_evidence を持つ義務は reference_derivation: true である",not orph,orph)

# ---- G1 の完了条件 ----
opens=[o['key'] for _,o in obs if o.get('open_question_ja')]
check("SR-30","未解決の open question が残っていない（G1 完了の条件）",not opens,opens)

# APPROVED を名乗る義務は承認根拠が揃っていなければならない（state の書き換えだけでは通さない）
secdg={r['id']:r['source_section_digest'] for r in reqs}
bad_appr=[]
for rid,o in obs:
    rv=o.get('review') or {}
    if rv.get('state')!='APPROVED': continue
    k=o['key']
    if not rv.get('reviewer'):      bad_appr.append(f"{k}: reviewer 未設定")
    if not rv.get('approved_at'):   bad_appr.append(f"{k}: approved_at 未設定")
    if rv.get('reviewer') and rv.get('reviewer')==o.get('authored_by'):
        bad_appr.append(f"{k}: reviewer==authored_by")
    if rv.get('source_spec')!=cov['spec']:           bad_appr.append(f"{k}: source_spec 不一致")
    if str(rv.get('spec_version'))!=str(cov['spec_version']): bad_appr.append(f"{k}: spec_version 不一致")
    if rv.get('source_selector') not in (rid,'#'+rid): bad_appr.append(f"{k}: source_selector 不一致")
    if rv.get('source_section_digest')!=secdg.get(rid):
        bad_appr.append(f"{k}: 承認時の節 digest が現在値と不一致（原文が変わった可能性）")
    if rv.get('obligation_digest')!=X.obligation_digest(o,preds['predicates']):
        bad_appr.append(f"{k}: 承認時の obligation digest が現在値と不一致（義務の内容が変わった）")
noauth=[o['key'] for _,o in obs if not o.get('authored_by')]
check("SR-25d","全 obligation に authored_by がある（reviewer≠author の判定に必要）",not noauth,noauth[:5])

# ============================================================================
# 承認（G1b）の検証
#
# 設計:
#   - 承認対象 = ある commit C における tests/{coverage,specs,predicates}.yaml
#   - 承認記録 = tests/approvals/g1.yaml。**C の外**（後続の commit）に置く
#     （承認記録を C の中に置くと、記録を追記した時点で C が変わる自己参照になる）
#   - 承認の真正性 = 署名された git オブジェクト（commit / tag）でのみ担保する
#     YAML 内の reviewer 文字列は自己申告であり、それだけでは承認と認めない
# ============================================================================
import subprocess,datetime as _dt,re as _re

def _git(*a,binary=False):
    try:
        return subprocess.run(['git','-C',ROOT]+list(a),
                              capture_output=True,text=not binary,timeout=20)
    except Exception:
        return None



def _iso_full(v):
    """文字列全体を ISO-8601 として解析し、タイムゾーン付き datetime のみ許可する。"""
    t=str(v)
    try:
        d=_dt.datetime.fromisoformat(t.replace('Z','+00:00'))
    except Exception:
        return False
    return d.tzinfo is not None

# 承認が守る対象。validator 自身も含める（検査器を弱める改変を検出するため）。
PROTECTED_PATHS=('tests/coverage.yaml','tests/specs.yaml','tests/predicates.yaml',
                 'tests/approvals/g1.yaml','tools/g1_validate.py','tools/g1_extract.py')
APPROVAL_REL='tests/approvals/g1.yaml' 
APPROVAL_PATH=os.path.join(ROOT,APPROVAL_REL)
appr=None; appr_src_problems=[]; _sig_info=None
if os.path.exists(APPROVAL_PATH):
    # ★ 承認記録の正本は「作業ツリーの内容」ではなく
    #    「その内容が入っている署名済み commit の中身」である。
    #    作業ツリーを信じると、署名済み commit を指したまま中身を書き換えられる。
    try: appr_peek=yaml.safe_load(open(APPROVAL_PATH,encoding='utf-8'))
    except Exception: appr_peek=None
    _lg=_git('log','-1','--format=%H','--',APPROVAL_REL)
    _sig_commit=_lg.stdout.strip() if _lg and _lg.returncode==0 else ''
    if not _sig_commit:
        appr_src_problems.append(f"{APPROVAL_REL} が git にコミットされていない（承認は commit されている必要がある）")
    else:
        _kind=((appr_peek or {}).get('evidence') or {}).get('kind')
        if _kind=='signed-tag':
            _tag=((appr_peek or {}).get('evidence') or {}).get('tag')
            if not _tag:
                appr_src_problems.append("evidence.kind=signed-tag には evidence.tag が必須")
                _v=None
            else:
                _v=_git('verify-tag',str(_tag))
                _rt=_git('rev-list','-n','1',str(_tag))
                if not _rt or _rt.returncode!=0 or _rt.stdout.strip()!=_sig_commit:
                    appr_src_problems.append(f"tag {_tag} が承認記録を含む commit {_sig_commit[:12]} を指していない")
        else:
            _v=_git('verify-commit',_sig_commit)
        _si=_git('log','-1','--format=%GS|%GK|%GT',_sig_commit)
        if _si and _si.returncode==0 and _si.stdout.strip():
            _p=_si.stdout.strip().split('|')
            _sig_info=dict(signer=_p[0] if len(_p)>0 else None,
                           key=_p[1] if len(_p)>1 else None,
                           trust=_p[2] if len(_p)>2 else None)
        if not _v or _v.returncode!=0:
            appr_src_problems.append(f"承認記録の署名検証に失敗（{_kind or 'signed-commit'} / commit {_sig_commit[:12]}）")
        else:
            _blob=_git('show',f'{_sig_commit}:{APPROVAL_REL}',binary=True)
            if not _blob or _blob.returncode!=0:
                appr_src_problems.append("署名済み commit から承認記録を読み出せない")
            else:
                appr=yaml.safe_load(_blob.stdout.decode('utf-8'))
                appr['_signed_commit']=_sig_commit
                # ★ 承認が守るのは承認記録だけではない。
                #   署名済み A の tree と「保護対象ファイルの現在値」を全て突き合わせる。
                #   これをしないと A の後で coverage を書き換え digest を再計算するだけで通る。
                for _rel in PROTECTED_PATHS:
                    _cur=os.path.join(ROOT,_rel)
                    _b=_git('show',f'{_sig_commit}:{_rel}',binary=True)
                    if not _b or _b.returncode!=0:
                        appr_src_problems.append(f"{_rel} が署名済み commit に存在しない"); continue
                    if not os.path.exists(_cur):
                        appr_src_problems.append(f"{_rel} が作業ツリーに存在しない"); continue
                    if X.sha(open(_cur,'rb').read())!=X.sha(_b.stdout):
                        appr_src_problems.append(f"{_rel} が署名済み承認 commit の内容と異なる（承認後に変更された）")
                # 追加・削除も検出する（tests/ 配下のファイル集合の一致）
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
                    # gitignore されているもの（tools/g1_authoring.py 等）は集合から除く
                    if _n:
                        _ci=subprocess.run(['git','-C',ROOT,'check-ignore','--stdin'],
                                           input='\n'.join(sorted(_n)),capture_output=True,text=True,timeout=30)
                        _n-=set(_ci.stdout.split())
                    if _a!=_n:
                        appr_src_problems.append(
                            f"{_dir}/ のファイル集合が署名済み commit と異なる"
                            f"（追加 {sorted(_n-_a)[:3]} / 削除 {sorted(_a-_n)[:3]}）")

# coverage.yaml 側の state は起票の記録であり、承認の正本ではない
state_claims=[o['key'] for _,o in obs if (o.get('review') or {}).get('state')!='PENDING_REVIEW']

approved_keys=set(); appr_problems=[]; appr_entries={}
if appr is None:
    if state_claims:
        appr_problems.append(f"承認記録 tests/approvals/g1.yaml が無いのに coverage.yaml が APPROVED を主張している（{len(state_claims)} 件）")
else:
    tc=str(appr.get('target_commit') or '')
    if not _re.fullmatch(r'[0-9a-f]{40}',tc):
        appr_problems.append("target_commit は 40 桁の完全な SHA-1 でなければならない（短縮 SHA を認めない）")
    else:
        r=_git('rev-parse','--verify','--quiet',tc+'^{commit}')
        if not r or r.returncode!=0 or r.stdout.strip()!=tc:
            appr_problems.append(f"target_commit {tc[:12]} が git に存在しない、または完全一致で解決できない")
        else:
            # 承認対象の内容を「対象 commit から」読み出して digest を突き合わせる
            for rel in ('tests/coverage.yaml','tests/specs.yaml','tests/predicates.yaml'):
                r2=_git('show',f'{tc}:{rel}',binary=True)
                if not r2 or r2.returncode!=0:
                    appr_problems.append(f"{rel} が対象 commit に存在しない"); continue
                want=(appr.get('artifact_digests') or {}).get(rel)
                got=X.sha(r2.stdout)
                if want!=got:
                    appr_problems.append(f"{rel}: 承認記録の digest が対象 commit の内容と不一致")
            # 承認対象 commit の coverage から義務 digest を再計算する
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
                    if k not in target_obs: appr_problems.append(f"{k}: 対象 commit に存在しない義務"); continue
                    if e.get('obligation_digest')!=X.obligation_digest(target_obs[k],cpred):
                        appr_problems.append(f"{k}: 承認 digest が対象 commit の内容と不一致"); continue
                    if not e.get('reviewer'): appr_problems.append(f"{k}: reviewer 未設定"); continue
                    if e['reviewer']==target_auth.get(k): appr_problems.append(f"{k}: reviewer==authored_by"); continue
                    if not _iso_full(e.get('approved_at')):
                        appr_problems.append(f"{k}: approved_at がタイムゾーン付き ISO-8601 でない"); continue
                    approved_keys.add(k)
                missing=[k for k in target_obs if k not in appr_entries]
                if missing: appr_problems.append(f"承認されていない義務が {len(missing)} 件（例 {missing[:3]}）")
    # 外部証拠: 署名された git オブジェクトのみを受け付ける
    ev=appr.get('evidence') or {}
    if ev.get('kind') not in ('signed-commit','signed-tag'):
        appr_problems.append("evidence.kind は signed-commit / signed-tag のいずれかで必須")
    if not (ev.get('reviewers') or []):
        appr_problems.append("evidence.reviewers は非空で必須（個別義務の reviewer と突き合わせるため）")
    if not ev.get('evidence_url'):
        appr_problems.append("evidence.evidence_url が必須（PR / レビュー記録の URL）")
    # 署名の検証は「承認記録が入っている commit」に対して実施済み（appr_src_problems）。
    # ★ evidence.ref のようなフィールドは置かない。記録の中に自分を含む commit の SHA を
    #   書くのは自己参照であり、署名し直すたびに SHA が変わって整合しない。
    #   署名済み commit は `git log -1 -- <path>` で一意に特定できる。
    if 'ref' in ev:
        appr_problems.append("evidence.ref は使用しない（自己参照になるため）。フィールドごと削除すること")
    if ev.get('evidence_url') and not _re.match(r'https://[^\s]+\.[^\s]+',str(ev['evidence_url'])):
        appr_problems.append("evidence_url が https の URL 形式でない")
    # per-obligation reviewer は evidence.reviewers に含まれていなければならない
    allowed=set(ev.get('reviewers') or [])
    bad_rv=sorted({e.get('reviewer') for e in (appr.get('approvals') or [])} - allowed - {None})
    if bad_rv: appr_problems.append(f"evidence.reviewers に無い reviewer: {bad_rv[:3]}")

_ut=_git('status','--porcelain','--untracked-files=all','tools')
_extra=[l[3:] for l in (_ut.stdout.splitlines() if _ut and _ut.returncode==0 else []) if l[3:].endswith('.py')]
check("SR-40","tools/ に未追跡・未コミットの Python モジュールがない（shadow import の防止）",
      not _extra,_extra[:5])

check("SR-38","承認が対象 commit の外にある署名付き記録に拘束されている",
      not appr_problems and not appr_src_problems,(appr_src_problems+appr_problems)[:6])

pending=[o['key'] for _,o in obs if o['key'] not in approved_keys]
check("SR-31","全 obligation が承認済み（G1 承認の条件）",not pending,f"{len(pending)}/{len(obs)} が未承認")

check("SR-39","coverage.yaml の g1_state が承認の実態と整合している",
      (cov.get('g1_state')=='APPROVED')==(not pending and not appr_problems),
      f"g1_state={cov.get('g1_state')} approved={len(approved_keys)}/{len(obs)}")

npass=sum(1 for c in R if c['result']=='PASS'); nfail=len(R)-npass
# 未承認・未解決は「G1 未完了」を示すものであり、作成フェーズの提出可否とは分ける
blocking=[c for c in R if c['result']=='FAIL' and c['id'] not in ('SR-30','SR-31')]
g1_ready = (not blocking) and (not opens) and (not pending) and cov.get('g1_state')=='APPROVED'
report=dict(task=":specReconcile",run_id=str(uuid.uuid4()),executed_at=NOW,
  validator="tools/g1_validate.py (生成処理から独立。値を書き戻さない)",
  mode="offline" if OFFLINE else "network",
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
      approved_obligations=len(approved_keys)) if appr else None),
  g1=dict(state=cov.get('g1_state'),open_questions=opens,unapproved=len(pending),
          blocking_failures=[c['id'] for c in blocking],
          complete=bool(g1_ready),
          complete_formula="no blocking failures AND no open questions AND all obligations approved via tests/approvals/g1.yaml AND coverage.g1_state == APPROVED"),
  checks=R,
  note="SR-30 / SR-31 は G1 の完了条件であり、作成フェーズでは FAIL のまま提出される。"
       "それ以外の FAIL は成果物の欠陥を意味する。")
os.makedirs(BUILD,exist_ok=True)
json.dump(report,open(os.path.join(BUILD,'spec-reconcile-report.json'),'w',encoding='utf-8'),ensure_ascii=False,indent=2)
print(f"{npass}/{len(R)} PASS  (blocking failures: {len(blocking)})")
for c in R:
    if c['result']=='FAIL': print(("  BLOCK " if c in blocking else "  g1-pending ")+c['id'],c['description'],'|',c['detail'][:90])
sys.exit(1 if blocking else 0)
