import re,json,html,hashlib,unicodedata,sys,datetime,os
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from g1_authoring import OBLIGATIONS
import g1_extract as X

SRC=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),'build','spec-cache','kantara-fedinterop-impl.html')
raw=open(SRC,'rb').read()
SRC_SHA='sha256:'+hashlib.sha256(raw).hexdigest()
t=raw.decode('utf-8')
t=re.sub(r'(?is)<(script|style).*?</\1>','',t)
t=t.replace('<em>','\x01').replace('</em>','\x02')
t=re.sub(r'(?i)<em [^>]*>','\x01',t)
t=re.sub(r'(?i)<(/?)(p|div|li|ul|ol|table|tr|td|th|h[1-6]|dt|dd|pre|br)[^>]*>','\n',t)
t=re.sub(r'(?s)<[^>]+>','',t)
t=html.unescape(t); t=unicodedata.normalize('NFC',t)
lines=[re.sub(r'[ \t]+',' ',l).strip() for l in t.split('\n')]
t='\n'.join(l for l in lines if l)

# 要件ラベルは行頭に単独で現れる。本文中の参照（例: SP05 のイタリック注記内の [IIP-SP09]）と区別する。
LABELS=[(m.group(1),m.start()) for m in re.finditer(r'(?m)^\[(IIP-[A-Z]+\d{2})\]$',t) if m.group(1)!='IIP-EXAMPLE01']
RIDS=[r for r,_ in LABELS]
assert len(RIDS)==len(set(RIDS))==69, (len(RIDS),len(set(RIDS)))
CUT=re.compile(r'^(\d+(\.\d+)*\.\s|Key Rollover$|Algorithm Support$|Avoiding Common Errors$|Metadata Exchange$|Metadata Usage$)')
sections={}
pos=LABELS
for k,(rid,p) in enumerate(pos):
    end = pos[k+1][1] if k+1<len(pos) else t.rindex('\n5. References')
    body=t[p+len(rid)+2:end].strip('\n')
    keep=[]
    for ln in body.split('\n'):
        if CUT.match(ln): break
        keep.append(ln)
    sections[rid]='\n'.join(keep).strip()

def spans_and_clean(s):
    """非規範(\x01..\x02)の位置を記録しつつマーカーを除去。offset はコードポイント単位。"""
    out=[];sp=[];i=0;depth=0;start=None
    for ch in s:
        if ch=='\x01':
            if depth==0: start=len(out)
            depth+=1
        elif ch=='\x02':
            depth-=1
            if depth==0: sp.append([start,len(out)])
        else:
            out.append(ch)
    return ''.join(out),sp

SEC={}
for rid,s in sections.items():
    clean,sp=spans_and_clean(s)
    SEC[rid]=dict(text=clean, non_normative=sp,
                  digest='sha256:'+hashlib.sha256(clean.encode('utf-8')).hexdigest(),
                  length=len(clean))

def find_clause(rid,clause,occurrence=None):
    """節内の全一致を列挙する。複数一致は occurrence（1-based）の明示を必須にする。"""
    txt=SEC[rid]['text']
    c=re.sub(r'\s+',' ',unicodedata.normalize('NFC',clause)).strip()
    hits=[(m.start(),m.end()) for m in re.finditer(re.escape(c),txt)]
    if not hits:
        pat=re.escape(c).replace(r'\ ',r'\s+')
        hits=[(m.start(),m.end()) for m in re.finditer(pat,txt)]
    if not hits: return None,0
    if len(hits)>1 and occurrence is None:
        raise SystemExit(f"AMBIGUOUS CLAUSE in {rid}: {len(hits)} matches for {c[:70]!r} — occurrence= を指定してください")
    idx=(occurrence or 1)-1
    if idx>=len(hits):
        raise SystemExit(f"occurrence={occurrence} は範囲外 ({len(hits)} 件) in {rid}")
    return hits[idx],len(hits)

errors=[];reqs={}
for o in OBLIGATIONS:
    rid=o['key'].rsplit('.',1)[0]
    specs_in = o.get('clauses') or [o['clause']]
    occs     = o.get('occurrences') or [o.get('occurrence')]*len(specs_in)
    out=[]; bad=False
    for ci,cl in enumerate(specs_in):
        r,nhits=find_clause(rid,cl,occs[ci] if ci<len(occs) else None)
        if r is None:
            errors.append(f"CLAUSE NOT FOUND {o['key']}[{ci}]: {cl[:60]}"); bad=True; break
        st,en=r
        sub=SEC[rid]['text'][st:en]
        if any(not(en<=a or st>=b) for a,b in SEC[rid]['non_normative']):
            errors.append(f"CLAUSE OVERLAPS NON-NORMATIVE {o['key']}[{ci}]")
        out.append(dict(start=st,end=en,occurrences=nhits,
            digest='sha256:'+hashlib.sha256(sub.encode('utf-8')).hexdigest()))
    if bad: continue
    o['_clauses']=sorted(out,key=lambda c:c['start'])
    o['_clause']=o['_clauses'][0]
    reqs.setdefault(rid,[]).append(o)

print(f"sections={len(SEC)} obligations={len(OBLIGATIONS)} errors={len(errors)}")
for e in errors[:20]: print("  !",e)



# ============================ 成果物の出力 ============================
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TESTS=os.path.join(ROOT,'tests'); os.makedirs(TESTS,exist_ok=True)
BUILD=os.path.join(ROOT,'build'); os.makedirs(BUILD,exist_ok=True)
NOW=datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()

def y(s):
    """YAML スカラーとして安全に出す。flow mapping 内でも壊れないよう保守的に引用する。"""
    s=str(s)
    safe=re.fullmatch(r'[A-Za-z0-9_./+-]*',s) and s.strip()==s and s!='' \
         and not re.fullmatch(r'(true|false|null|yes|no|on|off|~)',s,re.I)
    if safe: return s
    return '"'+s.replace('\\','\\\\').replace('"','\\"').replace('\n',' ')+'"'

def yq(s):
    """常に引用（digest / 版番号など、コロンを含みうる値に使う）"""
    return '"'+str(s).replace('\\','\\\\').replace('"','\\"')+'"' 

# ---------- specs.yaml ----------
SPECS = [
 ("kantara-fedinterop-impl","SAML V2.0 Implementation Profile for Federation Interoperability","Kantara Initiative","1.1","2019-12-18",
  "https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html", SRC_SHA, "primary"),
 ("SAML2Core","Assertions and Protocols for the OASIS SAML V2.0","OASIS","2.0 (saml-core-2.0-os)","2005-03-15",
  "http://docs.oasis-open.org/security/saml/v2.0/saml-core-2.0-os.pdf",
  "sha256:dc0890f88bfe862c9b39672bed76e2dcb0bedb03491cc41c161aea472b0468ab", "referenced"),
 ("SAML2Prof","Profiles for the OASIS SAML V2.0","OASIS","2.0 (saml-profiles-2.0-os)","2005-03-15",
  "http://docs.oasis-open.org/security/saml/v2.0/saml-profiles-2.0-os.pdf",
  "sha256:5df9b874551941c7f03cb9270e67ea44f426b44a8fa642b8cf261064cc6f7de2", "referenced"),
 ("SAML2Bind","Bindings for the OASIS SAML V2.0","OASIS","2.0 (saml-bindings-2.0-os)","2005-03-15",
  "http://docs.oasis-open.org/security/saml/v2.0/saml-bindings-2.0-os.pdf",
  "sha256:80dde7953739eeb306484888ea128dd214e2b4a5dd16fbcbb231ef7ae18fa3f1", "referenced"),
 ("SAML2Meta","Metadata for the OASIS SAML V2.0","OASIS","2.0 (saml-metadata-2.0-os)","2005-03-15",
  "http://docs.oasis-open.org/security/saml/v2.0/saml-metadata-2.0-os.pdf", "sha256:48656c21cc0cf26873ebec6792299de4f71c46ce5df73c707653b4c0b12117ae", "referenced"),
 ("SAML2Errata","SAML Version 2.0 Errata 05","OASIS","Errata 05","2012-05-01",
  "https://docs.oasis-open.org/security/saml/v2.0/errata05/os/saml-v2.0-errata05-os.pdf",
  "sha256:c9f6d7c4d6b147066a24715076ac8b62269429fae8e27d53a03cf0d73651ace3", "referenced"),
 ("SAML2MD-xsd","SAML V2.0 Metadata Schema","OASIS","2.0","2005-03-15",
  "http://docs.oasis-open.org/security/saml/v2.0/saml-schema-metadata-2.0.xsd", "sha256:204bc7991055dbb889307abbd2ff58022753897dd7064a4d1ca13eb737d2617a", "referenced"),
 ("SAML2-xsd","SAML V2.0 Assertion Schema","OASIS","2.0","2005-03-15",
  "http://docs.oasis-open.org/security/saml/v2.0/saml-schema-assertion-2.0.xsd", "sha256:006eb7553843cb7baa9b08da2a9d444346c0e982fb9d9293babe08ede680924b", "referenced"),
 ("SAML2MDIOP","SAML V2.0 Metadata Interoperability Profile","OASIS","1.0 (CS)","2009-08-01",
  "http://docs.oasis-open.org/security/saml/Post2.0/sstc-metadata-iop.pdf", "sha256:66a6f838429feb8fc96bf2d2d2741554bc030107aeb5ea4661d4881538dfe496", "referenced"),
 ("SAML2MetaAlgSup","SAML V2.0 Metadata Profile for Algorithm Support","OASIS","1.0 (cs01)","2011-02-01",
  "http://docs.oasis-open.org/security/saml/Post2.0/sstc-saml-metadata-algsupport-v1.0-cs01.pdf", "sha256:5f21e73e5d8fb2841a759905468cb27bbdb4637521ecdcb46c0744b1efb76245", "referenced"),
 ("SAML2ECP","SAML V2.0 Enhanced Client or Proxy Profile","OASIS","2.0 (cs01)","2013-08-01",
  "http://docs.oasis-open.org/security/saml/Post2.0/saml-ecp/v2.0/cs01/saml-ecp-v2.0-cs01.pdf", "sha256:9195b6bb98b3fe8f4dd532fa967e49c29c81ba519421afbfc6b85bbfd17f576a", "referenced"),
 ("MetaUi","SAML V2.0 Metadata Extensions for Login and Discovery User Interface","OASIS","1.0 (CS)","2012-04-01",
  "http://docs.oasis-open.org/security/saml/Post2.0/sstc-saml-metadata-ui/v1.0/sstc-saml-metadata-ui-v1.0.pdf", "sha256:e3ded72bc41e11a47e5371219cbcdd5b365e10d399793790ab5350f2b2f30cfc", "referenced"),
 ("MetaAttr","SAML V2.0 Metadata Extension for Entity Attributes","OASIS","1.0 (cs-01)","2009-08-01",
  "http://docs.oasis-open.org/security/saml/Post2.0/sstc-metadata-attr-cs-01.pdf", "sha256:15d332bf01534ab2b577974384f9f87704c8abb9b1717385a6e5bdc00ce933ea", "referenced"),
 ("SAML2ASLO","SAML V2.0 Asynchronous Single Logout Profile Extension","OASIS","1.0 (cs01)","2012-11-01",
  "http://docs.oasis-open.org/security/saml/Post2.0/saml-async-slo/v1.0/cs01/saml-async-slo-v1.0-cs01.pdf", "sha256:6b18895c247d2409203c7e17fd680d0e517df05566c85da059cb25a7a35df53c", "referenced"),
 ("MDQ","Metadata Query Protocol","IETF","draft-young-md-query-07","2017-07-01",
  "https://www.ietf.org/archive/id/draft-young-md-query-07.txt", "sha256:6007463c2733c644c5c8ab4665c09dfd434c68cf9b2eb183c27a76712a96c3f8", "referenced-draft"),
 ("SAML-MDQ","SAML Profile for the Metadata Query Protocol","IETF","draft-young-md-query-saml-07","2014-10-01",
  "https://www.ietf.org/archive/id/draft-young-md-query-saml-07.txt", "sha256:ffdf5b1dcecec482e4100a76b97ae80d96fffd237a9437c101010b766c52bf49", "referenced-draft"),
 ("IdPDisco","Identity Provider Discovery Service Protocol and Profile","OASIS","1.0 (CS)","2008-03-01",
  "http://docs.oasis-open.org/security/saml/Post2.0/sstc-saml-idp-discovery.pdf", "sha256:8b631f0fff50d5268872bd59f6bab40f511ae274ea0f3212046cbcacee8dd3fa", "referenced"),
 ("SAML-EC","SAML Enhanced Client SASL and GSS-API Mechanisms","IETF","draft-ietf-kitten-sasl-saml-ec-16","2017-10-01",
  "https://www.ietf.org/archive/id/draft-ietf-kitten-sasl-saml-ec-16.txt", "sha256:7c3266f6e19445e9e5f06d637a73769fcd99dd35865101544be8ec6444d625a6", "referenced-draft"),
 ("RFC2617","HTTP Authentication: Basic and Digest Access Authentication","IETF","RFC 2617","1999-06-01",
  "https://www.ietf.org/rfc/rfc2617.txt", "sha256:cf5492136782d9e9fce492c254ca39e2c93328cf38c4dd006039abb5d8e27ba7", "referenced"),
 ("XMLSig","XML-Signature Syntax and Processing 1.1","W3C","1.1","2013-04-11",
  "https://www.w3.org/TR/xmldsig-core1/", "sha256:4924a334deb880e7e18b54cd6db037aa6b4d0186e626a26d636c124f8a2e07a3", "referenced"),
 ("XMLEnc","XML Encryption Syntax and Processing 1.1","W3C","1.1","2013-04-11",
  "https://www.w3.org/TR/xmlenc-core1/", "sha256:40e83298cc2e53c565bebf8b8345c14edabea77d56a3a3698ca438730afec1ce", "referenced"),
 ("RFC4051","Additional XML Security Uniform Resource Identifiers","IETF","RFC 4051","2005-04-01",
  "https://www.ietf.org/rfc/rfc4051.txt", "sha256:4cb72c3ca467d4a82c8e1d046d09eb05cbbe74b249f94fc2a81bcfed9e6eb8e3", "referenced"),
 ("RFC7457","Summarizing Known Attacks on TLS and DTLS","IETF","RFC 7457","2015-02-01",
  "https://www.ietf.org/rfc/rfc7457.txt", "sha256:f90360738aaa90dc2071e82bc089ffd1169995e7104ae252cd596444da49685f", "referenced"),
 ("BetterCrypto","Applied Crypto Hardening","BetterCrypto.org","undated (living document)",None,
  "https://bettercrypto.org", None, "referenced-unversioned"),
]
# ---- 参照仕様の節ダイジェストを実測して reference_evidence に埋める ----
_ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_SPECURL={}
def _spec_text(key):
    if key not in _SPECURL:
        url=[u for k,_,_,_,_,u,_,_ in SPECS if k==key]
        if not url: raise SystemExit(f'unknown spec {key}')
        raw,_p=X.fetch(_ROOT,key,url[0],mode='cache-first')
        if raw is None: raise SystemExit(f'{key} が build/spec-cache/ にありません。先に取得してください')
        _SPECURL[key]=X.normalize(raw,url[0])
    return _SPECURL[key]
for o in OBLIGATIONS:
    for ev in o.get('reference_evidence') or []:
        sec=X.section(_spec_text(ev['spec']),ev['locator'])
        ev['section_digest']=X.sha(sec)
        ev['section_length']=len(sec)


# ---- 起票時の安定性検証: pin する digest が再取得で再現するか（動的ページを弾く） ----
if os.environ.get('G1_VERIFY_STABILITY')=='1':
    import tempfile
    for _k,_t,_p,_v,_d,_u,_dg,_r in SPECS:
        if not _u or not _dg: continue
        _a,_pa=X.fetch(_ROOT,_k,_u,mode='network')
        _b,_pb=X.fetch(_ROOT,_k+'__stab',_u,mode='network')
        if X.sha(_a)!=X.sha(_b):
            raise SystemExit(f"UNSTABLE SOURCE {_k}: 2 回の取得でバイト列が一致しない。不変 URL を使うこと")
        if X.sha(_a)!=_dg:
            raise SystemExit(f"DIGEST DRIFT {_k}: {X.sha(_a)} != {_dg}")
        try: os.remove(_pb)
        except OSError: pass
    print("stability: all pinned sources reproducible")

L=["# tests/specs.yaml — 仕様カタログ（G1 成果物）",
   "# 生成: build_g1.py / 手編集しない",
   f"generated_at: {NOW}",
   "schema_version: 1","specs:"]
for k,title,pub,ver,date,url,dg,role in SPECS:
    L+= [f"  {k}:",
         f"    title: {y(title)}",
         f"    publisher: {y(pub)}",
         f"    version: {yq(ver)}",
         f"    date: {date}",
         f"    url: {y(url) if url else 'null'}",
         f"    role: {role}"]
    if url: pass
    if dg: L.append(f"    source_digest: {yq(dg)}")
    if role=='referenced-unversioned':
        L.append("    note: 版のない生きた文書。判定の根拠には使わず、参考情報としてのみ引用する")
    if role=='referenced-draft':
        L.append("    note: インターネットドラフト。版が変わると章番号・要素定義が変わりうるため版を固定する。")
        L.append("    url_note: tools.ietf.org の HTML は動的レンダリングでバイト列が安定しないため、不変のアーカイブ .txt を使う")
L.append("")
open(os.path.join(TESTS,'specs.yaml'),'w',encoding='utf-8').write('\n'.join(L))

# ---------- predicates.yaml ----------
PRED = [
 ("claims_mdq_support","CLAIM_BASED","対象が MDQ 対応を表明しているか",
  ["declared_features.mdq"],[],"原文が 'Implementations that claim support for this protocol' と述べており、申告そのものが条件"),
 ("supports_outbound_encryption","CAPABILITY_BASED","対象が outbound 暗号化に対応しているか",
  ["declared_features.assertion_encryption"],
  ["target_emitted: saml:EncryptedAssertion","target_emitted: saml:EncryptedID","target_emitted: saml:EncryptedAttribute"],
  "原文は 'If an implementation supports outbound encryption'。★ 観測は方向付きでなければならない: 対象が *送信した* 暗号要素のみが証拠になる。対象メタデータの KeyDescriptor use=encryption は『暗号文を受信・復号できる』証拠であって outbound 生成能力の証拠ではないため使わない"),
 ("claims_slo_support_sp","CLAIM_BASED","SP が SLO profile への対応を表明しているか",
  ["declared_features.single_logout"],[],
  "原文が 'Service Providers that claim support for this profile'。申告が真理値そのもの"),
 ("supports_slo_sp","CAPABILITY_BASED","SP が実際に SLO profile に対応しているか",
  ["declared_features.single_logout"],
  ["target_metadata_has: md:SPSSODescriptor/md:SingleLogoutService","target_emitted: samlp:LogoutRequest","target_consumed: samlp:LogoutRequest"],
  "原文は 'Service Providers that support the ... profile'（claim ではない）ため CAPABILITY_BASED"),
 ("supports_cbc","CAPABILITY_BASED","対象が AES-CBC ブロック暗号に対応しているか",
  ["declared_features.cbc"],
  ["target_emitted_encryption_method: aes128-cbc","target_emitted_encryption_method: aes256-cbc",
   "target_accepted_encryption_method: aes128-cbc","target_accepted_encryption_method: aes256-cbc"],
  "IIP-ALG05.b の条件。CBC 非対応なら NOT_APPLICABLE であって違反ではない"),
 ("peer_declares_algorithm_support","CAPABILITY_BASED","ピア（Samlier）のメタデータがアルゴリズム対応を宣言しているか",
  [],["suite_metadata_variant_declares_alg: true"],
  "IIP-MD10 の 'If a SAML peer has declared algorithm support'。Suite 側の構成で決まるため常に決定可能"),
 ("setting_supported_by_implementation","CAPABILITY_BASED","当該メタデータ要素に対応する設定を対象が備えているか",
  ["declared_features.supported_settings"],
  ["target_behaviour_changed_on_metadata_edit: true"],
  "IIP-SSO06 の (b) 'corresponds to settings supported by the implementation'。要素ごとに評価する"),
 ("not_token_translation_proxy","CLASSIFICATION_BASED","対象が token translation Proxy ではないか",
  ["target.kind"],[],
  "IIP-IDP13 末尾の 'This requirement does not apply to token translation Proxies.'。観測材料が存在しないため明示的な除外申告のみが FALSE を作れる",
  "The target was declared to be a token translation Proxy, to which IIP-IDP13 does not apply. This was not verified by the Suite."),
 ("supports_name_identifier_management","CAPABILITY_BASED","対象が SAML2Core 3.6 の Name Identifier Management に対応しているか",
  ["declared_features.name_identifier_management"],
  ["target_metadata_has: md:IDPSSODescriptor/md:ManageNameIDService",
   "target_consumed: samlp:ManageNameIDRequest",
   "target_emitted: samlp:ManageNameIDResponse"],
  "IIP-SSO05.a5 の条件。SAML2Core 8.3.7 の SPProvidedID は『SP が設定した代替識別子』を前提とし、その設定手段が §3.6 しかない。"
  "★ 観測は方向付き: 対象が ManageNameIDRequest を *受理し* ManageNameIDResponse を *返した* ことが能力の証拠。"
  "メタデータの ManageNameIDService だけでは宣言であって能力の観測ではないが、"
  "IIP-MD01 が『メタデータは実際の設定を反映する』ことを求めるため補助証拠として採る"),
 ("reissues_foreign_persistent_identifier","CAPABILITY_BASED","対象が他エンティティ生成の persistent 識別子を再発行するか",
  ["declared_features.proxy_idp"],
  ["target_reissued_upstream_persistent_nameid: true"],
  "IIP-SSO05.a6 / .a7 の条件。SAML2Core 8.3.7 の再発行規則は『a different system entity might later issue its own "
  "protocol message or assertion containing the identifier』に該当する構成でのみ適用される。"
  "★ 観測は方向付き: 上流 Samlier-IdP が発行した NameID と同一の値を、対象が自身の Assertion で送出したことだけが証拠になる。"
  "対象が Proxy を名乗るだけでは能力の観測ではない"),
]
L=["# tests/predicates.yaml — 条件述語の固定集合（G1 成果物）",
   "# 生成: build_g1.py / 手編集しない",
   f"generated_at: {NOW}","schema_version: 1",
   "# kind: CLAIM_BASED      = 申告そのものが条件（observed 不要）",
   "#       CAPABILITY_BASED = 実際の能力が条件（observed 必須。declaration-only FALSE は UNKNOWN）",
   "#       CLASSIFICATION_BASED = 製品分類が条件（declaration_only_exclusion のみ FALSE 可）",
   "predicates:"]
for _p in PRED:
    name,kind,desc,decl,obs,note = _p[:6]
    excl = _p[6] if len(_p)>6 else None
    assert (kind=="CLASSIFICATION_BASED")==(excl is not None), \
        f"{name}: CLASSIFICATION_BASED は declaration_only_exclusion.statement_en が必須、それ以外は持ってはならない"
    L+=[f"  {name}:",f"    kind: {kind}",f"    description_ja: {y(desc)}"]
    L.append("    declared:"+(" []" if not decl else ""))
    for d in decl: L.append(f"      - {y(d)}")
    L.append("    observed:"+(" []" if not obs else ""))
    for o in obs: L.append(f"      - {y(o)}")
    L.append("    on_conflict: inconsistent")
    if kind=="CLASSIFICATION_BASED":
        L+=["    declaration_only_exclusion:","      allowed: true","      requires_reason: true",
            f"      statement_en: {yq(excl)}"]
    L.append(f"    rationale_ja: {y(note)}")
L.append("")
open(os.path.join(TESTS,'predicates.yaml'),'w',encoding='utf-8').write('\n'.join(L))
print("wrote specs.yaml / predicates.yaml")

# ---------- coverage.yaml ----------
SECTION_OF={}
for rid in RIDS:
    if rid.startswith('IIP-G'): SECTION_OF[rid]=('2.1','Common / General')
    elif rid.startswith('IIP-MD'): SECTION_OF[rid]=('2.2','Common / Metadata and Trust Management')
    elif rid.startswith('IIP-SSO'): SECTION_OF[rid]=('2.3','Common / Web Browser SSO')
    elif rid.startswith('IIP-EXT'): SECTION_OF[rid]=('2.4','Common / Extensibility')
    elif rid.startswith('IIP-ALG'): SECTION_OF[rid]=('2.5','Common / Cryptographic Algorithms')
    elif rid.startswith('IIP-SP'):
        n=int(rid[6:]); SECTION_OF[rid]=(('3.2','Service Provider / Single Logout') if n>=14 else ('3.1','Service Provider / Web Browser SSO'))
    else:
        n=int(rid[7:])
        SECTION_OF[rid]=(('4.3','Identity Provider / Single Logout') if n>=17 else
                         ('4.2','Identity Provider / Enhanced Client or Proxy') if n>=13 else
                         ('4.1','Identity Provider / Web Browser SSO'))

MUSTC={'MUST','MUST_NOT','REQUIRED'}
CORE_SECTIONS={'2.1','2.2','2.3','2.4','2.5'}
# linked_obligations で使える種別。増やすときは docs/03 §リンクの意味 と
# g1_validate.py の SR-22g を同時に更新すること（意味の定義がない種別を成果物に入れない）。
LINK_KINDS = {'inherit_variants'}

def level_assignment(rid,o):
    """Core = MUST_CLASS かつ SLO/ECP/Discovery 以外。Samlier 独自分類。"""
    sec=SECTION_OF[rid][0]
    slo_ecp = sec in ('3.2','4.2','4.3') or rid in ('IIP-SP04',)
    core = (o['level'] in MUSTC) and not slo_ecp
    return {r:('core' if core else 'full') for r in o['roles']}

L=["# tests/coverage.yaml — 要件カタログ（G1 成果物 / 判定レベルの唯一の出典）",
   "# 生成: build_g1.py（原文からオフセットと digest を解決）/ 手編集しない",
   f"generated_at: {NOW}",
   "schema_version: 1",
   "g1_state: PENDING_REVIEW",
   "# 作成者は reviewer / approved_at を埋めない。別のレビュアーが原文と直接照合して承認する。",
   "spec: kantara-fedinterop-impl",
   "spec_version: \"1.1\"",
   "source_digest: "+yq(SRC_SHA),
   "catalog_digest: null   # specs.yaml + predicates.yaml 全体（後処理で埋める）",
   "# 承認記録はこのファイルに置かない。tests/approvals/g1.yaml（対象 commit の外）に置く。",
   "clause_offset_convention:",
   "  base: normalized requirement section",
   "  start: 0-based",
   "  end: exclusive",
   "  unit: unicode-code-point",
   "  empty_range: forbidden",
   "  digest: sha256 of the extracted substring encoded as UTF-8",
   "section_normalization:",
   "  boundary: from the line '[IIP-xxNN]' to just before the next such line or the next numbered/named heading",
   "  steps: [strip-html-tags, unescape-entities, NFC, collapse-horizontal-whitespace, drop-empty-lines]",
   "  non_normative: '<em> spans are recorded as non_normative_spans and MUST NOT overlap any source_clause'",
   "requirements:"]

nob=0
for rid in RIDS:
    sec,secname=SECTION_OF[rid]
    S=SEC[rid]
    L+= [f"  - id: {rid}",
         f"    section: {y(sec)}",
         f"    section_name: {y(secname)}",
         f"    anchor: {y('#'+rid)}",
         f"    source_section_digest: {yq(S['digest'])}",
         f"    source_section_length: {S['length']}"]
    L.append("    non_normative_spans:"+(" []" if not S['non_normative'] else ""))
    for a,b in S['non_normative']:
        L.append(f"      - {{ start: {a}, end: {b} }}")
    L.append("    obligations:")
    for o in reqs.get(rid,[]):
        nob+=1
        c=o['_clause']
        L+= [f"      - key: {o['key']}",
             f"        roles: [{', '.join(o['roles'])}]",
             f"        level: {o['level']}",
             f"        testability: {o['testability']}"]
        cond=o.get('condition')
        if cond:
            L+= ["        condition:",
                 f"          predicate: {cond['predicate']}",
                 f"          predicate_kind: {cond['kind']}"]
        else:
            L.append("        condition: null")
        if o['testability']=='CONFIG':
            assert o.get('config_semantics'), f"{o['key']}: CONFIG は configuration_failure_semantics の明示が必須"
        if o.get('config_semantics'):
            L.append(f"        configuration_failure_semantics: {o['config_semantics']}")
        L+= [f"        summary_en: {y(o['summary_en'])}",
             f"        summary_ja: {y(o['summary_ja'])}"]
        if o.get('spec_item'):
            if 'reference_derivation' not in o:
                raise SystemExit(f"{o['key']}: references_spec を持つ義務は authoring 入力で "
                                 f"reference_derivation を明示すること（生成側で推測しない）")
            rd=o['reference_derivation']
            if rd is True and not o.get('reference_evidence'):
                raise SystemExit(f"{o['key']}: reference_derivation=True なのに reference_evidence がない")
            if rd is False and o.get('reference_evidence'):
                raise SystemExit(f"{o['key']}: reference_derivation=False なのに reference_evidence がある")
            if rd is False and not o.get('reference_derivation_note'):
                raise SystemExit(f"{o['key']}: reference_derivation=False には理由（reference_derivation_note）が必須。"
                                 f"参照仕様を挙げながら根拠を持たない理由を書くこと")
            L.append(f"        references_spec: {y(o['spec_item'])}")
            L.append(f"        reference_derivation: {'true' if rd else 'false'}")
            if rd is False:
                L.append(f"        reference_derivation_note: {y(o['reference_derivation_note'])}")
        if o.get('reference_evidence'):
            L.append("        reference_evidence:")
            for ev in o['reference_evidence']:
                L+= [f"          - spec: {y(ev['spec'])}",
                     f"            locator: {yq(ev['locator'])}",
                     f"            section_digest: {yq(ev['section_digest'])}",
                     f"            basis_ja: {y(ev['basis_ja'])}"]
        if o.get('applicability_note_en'):
            L.append(f"        applicability_note_en: {y(o['applicability_note_en'])}")
            L.append(f"        applicability_note_ja: {y(o['applicability_note_ja'])}")
        la=level_assignment(rid,o)
        L.append("        level_assignment: { "+", ".join(f"{k}: {v}" for k,v in la.items())+" }")
        L.append("        source_clauses:")
        for cc in o['_clauses']:
            L.append(f"          - {{ start: {cc['start']}, end: {cc['end']}, digest: {yq(cc['digest'])}, occurrences: {cc['occurrences']} }}")
        if o['testability']=='NOT_OBSERVABLE':
            L.append(f"        not_observable_reason_en: {y(o['not_observable_reason_en'])}")
            L.append(f"        not_observable_reason_ja: {y(o['not_observable_reason_ja'])}")
            L.append("        required_variants: []")
        else:
            L.append("        required_variants:")
            for v in o.get('variants',[]):
                # ★ 安定 ID: 説明文の内容から導出する。
                #   配列インデックスだと並び替えで参照が壊れる。
                #   内容が変われば ID も変わる（= variant が変わったということ）。
                # 義務キーを混ぜて、別義務の同一説明文が衝突しないようにする
                vid='v-'+hashlib.sha256((o['key']+'\x00'+v).encode('utf-8')).hexdigest()[:10]
                L.append(f"          - id: {vid}")
                L.append(f"            description_ja: {y(v)}")
        if o.get('linked_obligations'):
            L.append("        linked_obligations:")
            for lk in o['linked_obligations']:
                if lk.get('kind') not in LINK_KINDS:
                    raise SystemExit(f"{o['key']}: linked_obligations.kind は {sorted(LINK_KINDS)} のいずれか"
                                     f"（受け取った値: {lk.get('kind')!r}）。"
                                     f"新しい種別を足すには docs/03 §リンクの意味 と g1_validate.py SR-22g を先に更新すること")
                if not lk.get('note_ja'):
                    raise SystemExit(f"{o['key']}: linked_obligations には note_ja（何を取り込むかの説明）が必須")
                L+= [f"          - obligation: {lk['obligation']}",
                     f"            kind: {lk['kind']}",
                     f"            note_ja: {y(lk['note_ja'])}"]
        if o.get('controls'):
            L.append("        controls:")
            for v in o['controls']: L.append(f"          - {y(v)}")
        if o.get('notes_ja'): L.append(f"        notes_ja: {y(o['notes_ja'])}")
        if o.get('open_question'): L.append(f"        open_question_ja: {y(o['open_question'])}")
        L+= ["        authored_by: samlier-g1-builder",
             "        review:",
             "          # state は起票側の記録。承認の正本は tests/approvals/g1.yaml",
             "          state: PENDING_REVIEW",
             "          reviewer: null",
             "          approved_at: null",
             "          obligation_digest: null",
             "          source_spec: kantara-fedinterop-impl",
             "          spec_version: \"1.1\"",
             f"          source_selector: {y('#'+rid)}",
             f"          source_section_digest: {yq(SEC[rid]['digest'])}"]
L.append("")
open(os.path.join(TESTS,'coverage.yaml'),'w',encoding='utf-8').write('\n'.join(L))
print("wrote coverage.yaml:",nob,"obligations /",len(RIDS),"requirements")
# ---- 後処理: 書き出した coverage.yaml を読み直し、obligation_digest を実測して埋める ----
# 検証側と同じ「YAML から見える値」で計算することで、author / validator の一致を保証する。
try:
    import yaml as _yaml
except ImportError:
    raise SystemExit("PyYAML が必要です:  .venv/bin/pip install -r tools/requirements.txt")
_path=os.path.join(TESTS,'coverage.yaml')
_doc=_yaml.safe_load(open(_path,encoding='utf-8'))
_preds=_yaml.safe_load(open(os.path.join(TESTS,'predicates.yaml'),encoding='utf-8'))['predicates']
_digests=[X.obligation_digest(o,_preds) for r in _doc['requirements'] for o in r['obligations']]
_lines=open(_path,encoding='utf-8').read().split('\n')
_i=0
for _n,_l in enumerate(_lines):
    if _l=='          obligation_digest: null':
        _lines[_n]='          obligation_digest: "%s"'%_digests[_i]; _i+=1
assert _i==len(_digests), (_i,len(_digests))
open(_path,'w',encoding='utf-8').write('\n'.join(_lines))
# カタログ全体の digest（specs.yaml + predicates.yaml）を coverage.yaml に刻む
_specs=_yaml.safe_load(open(os.path.join(TESTS,'specs.yaml'),encoding='utf-8'))
_cat=X.catalog_digest(_specs,{'predicates':_preds})
_txt=open(_path,encoding='utf-8').read().replace('catalog_digest: null','catalog_digest: "%s"'%_cat,1)
open(_path,'w',encoding='utf-8').write(_txt)
print("filled obligation_digest:",_i,"/ catalog_digest:",_cat[:22])


print("done. 次に:  python3 tools/g1_docgen.py  および  python3 tools/g1_validate.py")
