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

# Requirement labels appear alone at the beginning of a line. This excludes
# references embedded in prose, such as [IIP-SP09] in the italic SP05 note.
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
    """Remove markers while recording non-normative spans in code points."""
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
    """Find every clause occurrence; ambiguous matches require a 1-based index."""
    txt=SEC[rid]['text']
    c=re.sub(r'\s+',' ',unicodedata.normalize('NFC',clause)).strip()
    hits=[(m.start(),m.end()) for m in re.finditer(re.escape(c),txt)]
    if not hits:
        pat=re.escape(c).replace(r'\ ',r'\s+')
        hits=[(m.start(),m.end()) for m in re.finditer(pat,txt)]
    if not hits: return None,0
    if len(hits)>1 and occurrence is None:
        raise SystemExit(f"AMBIGUOUS CLAUSE in {rid}: {len(hits)} matches for {c[:70]!r}; specify occurrence=")
    idx=(occurrence or 1)-1
    if idx>=len(hits):
        raise SystemExit(f"occurrence={occurrence} is out of range ({len(hits)} matches) in {rid}")
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



# ============================ Artifact output ============================
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TESTS=os.path.join(ROOT,'tests'); os.makedirs(TESTS,exist_ok=True)
BUILD=os.path.join(ROOT,'build'); os.makedirs(BUILD,exist_ok=True)
NOW=datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()

def y(s):
    """Render a conservatively quoted YAML scalar, including in flow mappings."""
    s=str(s)
    safe=re.fullmatch(r'[A-Za-z0-9_./+-]*',s) and s.strip()==s and s!='' \
         and not re.fullmatch(r'(true|false|null|yes|no|on|off|~)',s,re.I)
    if safe: return s
    return '"'+s.replace('\\','\\\\').replace('"','\\"').replace('\n',' ')+'"'

def yq(s):
    """Always quote values such as digests and versions that may contain colons."""
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
 ("SAML2P-xsd","SAML V2.0 Protocol Schema","OASIS","2.0","2005-03-15",
  "http://docs.oasis-open.org/security/saml/v2.0/saml-schema-protocol-2.0.xsd",
  "sha256:554250583cd5eacc6ce5f094f6ff50fc2547972c436dc96e2e7eb41abf2c817e", "referenced"),
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
# Resolve reference-section digests from the pinned source documents.
_ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_SPECURL={}
def _spec_text(key):
    if key not in _SPECURL:
        url=[u for k,_,_,_,_,u,_,_ in SPECS if k==key]
        if not url: raise SystemExit(f'unknown spec {key}')
        raw,_p=X.fetch(_ROOT,key,url[0],mode='cache-first')
        if raw is None: raise SystemExit(f'{key} is absent from build/spec-cache; fetch it first')
        _SPECURL[key]=X.normalize(raw,url[0])
    return _SPECURL[key]
for o in OBLIGATIONS:
    for ev in o.get('reference_evidence') or []:
        sec=X.section(_spec_text(ev['spec']),ev['locator'])
        ev['section_digest']=X.sha(sec)
        ev['section_length']=len(sec)


# Optional authoring-time reproducibility check for every pinned source.
if os.environ.get('G1_VERIFY_STABILITY')=='1':
    import tempfile
    for _k,_t,_p,_v,_d,_u,_dg,_r in SPECS:
        if not _u or not _dg: continue
        _a,_pa=X.fetch(_ROOT,_k,_u,mode='network')
        _b,_pb=X.fetch(_ROOT,_k+'__stab',_u,mode='network')
        if X.sha(_a)!=X.sha(_b):
            raise SystemExit(f"UNSTABLE SOURCE {_k}: two fetches produced different bytes; use an immutable URL")
        if X.sha(_a)!=_dg:
            raise SystemExit(f"DIGEST DRIFT {_k}: {X.sha(_a)} != {_dg}")
        try: os.remove(_pb)
        except OSError: pass
    print("stability: all pinned sources reproducible")

L=["# tests/specs.yaml — canonical G1 specification catalog",
   "# Generated by tools/g1_author.py. Do not edit manually.",
   f"generated_at: {NOW}",
   "schema_version: 2","specs:"]
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
        L.append("    note: Unversioned living document; cited only as background and never as verdict evidence.")
    if role=='referenced-draft':
        L.append("    note: Internet-Draft pinned to a specific revision because section numbers and element definitions may change.")
        L.append("    url_note: Use the immutable archive text because the tools.ietf.org HTML representation is dynamically rendered and byte-unstable.")
L.append("")
open(os.path.join(TESTS,'specs.yaml'),'w',encoding='utf-8').write('\n'.join(L))

# ---------- predicates.yaml ----------
# Human-readable predicate text is canonical English. The declared/observed
# paths and predicate kinds are normative migration invariants.
PRED = [
 ("claims_mdq_support","CLAIM_BASED","Whether the target claims support for MDQ",
  ["declared_features.mdq"],[],"The source clause says 'Implementations that claim support for this protocol'; the declaration itself determines applicability."),
 ("supports_outbound_encryption","CAPABILITY_BASED","Whether the target supports outbound encryption",
  ["declared_features.assertion_encryption"],
  ["target_emitted: saml:EncryptedAssertion","target_emitted: saml:EncryptedID","target_emitted: saml:EncryptedAttribute"],
  "The source clause says 'If an implementation supports outbound encryption'. Evidence is directional: only encrypted elements emitted by the target demonstrate outbound capability. A target metadata KeyDescriptor with use=encryption demonstrates receiving/decryption capability, not outbound generation, and is therefore excluded."),
 ("claims_slo_support_sp","CLAIM_BASED","Whether the SP claims support for the SLO profile",
  ["declared_features.single_logout"],[],
  "The source clause says 'Service Providers that claim support for this profile'; the declaration itself determines applicability."),
 ("supports_slo_sp","CAPABILITY_BASED","Whether the SP actually supports the SLO profile",
  ["declared_features.single_logout"],
  ["target_metadata_has: md:SPSSODescriptor/md:SingleLogoutService","target_emitted: samlp:LogoutRequest","target_consumed: samlp:LogoutRequest"],
  "The source clause says 'Service Providers that support the ... profile', not 'claim support', so this is capability-based."),
 ("supports_slo_initiation_sp","CAPABILITY_BASED","Whether the SP can initiate SLO as a session participant and issue a LogoutRequest",
  ["declared_features.single_logout"],
  ["target_emitted: samlp:LogoutRequest"],
  "Applicability condition for the session-participant initiator rules in SAML2Prof 4.4.3.1. It is separate from supports_slo_sp so that initiator rules are not imposed on an SP that implements only reception. Only a LogoutRequest actually emitted by the target is evidence; target_consumed is excluded."),
 ("supports_cbc","CAPABILITY_BASED","Whether the target supports AES-CBC block encryption",
  ["declared_features.cbc"],
  ["target_emitted_encryption_method: aes128-cbc","target_emitted_encryption_method: aes256-cbc",
   "target_accepted_encryption_method: aes128-cbc","target_accepted_encryption_method: aes256-cbc"],
  "Condition for IIP-ALG05.b. Lack of CBC support makes the obligation not applicable; it is not a violation."),
 ("peer_declares_algorithm_support","CAPABILITY_BASED","Whether the Samlier peer metadata declares algorithm support",
  [],["suite_metadata_variant_declares_alg: true"],
  "IIP-MD10 says 'If a SAML peer has declared algorithm support'. The Suite controls this input, so it is always decidable."),
 ("setting_supported_by_implementation","CAPABILITY_BASED","Whether the target provides a setting corresponding to the metadata element",
  ["declared_features.supported_settings"],
  ["target_behaviour_changed_on_metadata_edit: true"],
  "IIP-SSO06(b) says 'corresponds to settings supported by the implementation'. Evaluate this separately for each element."),
 ("not_token_translation_proxy","CLASSIFICATION_BASED","Whether the target is not a token translation Proxy",
  ["target.kind"],[],
  "IIP-IDP13 ends with 'This requirement does not apply to token translation Proxies.' No protocol observation can establish the negative classification, so only an explicit declared exclusion can make the predicate false.",
  "The target was declared to be a token translation Proxy, to which IIP-IDP13 does not apply. This was not verified by the Suite."),
 ("supports_name_identifier_management","CAPABILITY_BASED","Whether the target supports Name Identifier Management from SAML2Core 3.6",
  ["declared_features.name_identifier_management"],
  ["target_metadata_has: md:IDPSSODescriptor/md:ManageNameIDService",
   "target_consumed: samlp:ManageNameIDRequest",
   "target_emitted: samlp:ManageNameIDResponse"],
  "Condition for IIP-SSO05.a5. SPProvidedID in SAML2Core 8.3.7 presupposes an alternative identifier established by the SP, and section 3.6 is the available mechanism. Evidence is directional: accepting ManageNameIDRequest and emitting ManageNameIDResponse demonstrates the capability. ManageNameIDService in metadata is a declaration rather than a behavioral observation, but it is retained as supporting evidence because IIP-MD01 requires metadata to reflect actual configuration."),
 ("proxies_to_non_saml_provider","CLASSIFICATION_BASED","Whether the target proxies to a non-SAML upstream provider",
  ["target.upstream_kind"],[],
  "Some normative clauses in SAML2Core 3.4.1.5.1 are explicitly scoped by 'If the authenticating identity provider is not a SAML identity provider' and do not apply when every upstream provider is a SAML IdP. An earlier capability-based design treated an AuthenticatingAuthority value that could not be resolved in SAML metadata as evidence, but that is also true for an unregistered or unavailable SAML IdP. The upstream protocol type is not observable on the SAML protocol surface, so only a configuration attestation can determine the classification. The default is true; making it false requires an explicit exclusion with a reason.",
  "The target was declared to proxy only to SAML identity providers, so the rules that [SAML2Core] section "
  "3.4.1.5.1 scopes to a non-SAML authenticating identity provider do not apply. This was not verified by the Suite."),
 ("uses_small_integer_sessionindex","CAPABILITY_BASED","Whether the target uses the small-positive-integer or repeated-constant SessionIndex scheme",
  ["declared_features.sessionindex_scheme"],
  ["target_emitted_sessionindex_is_small_integer: true"],
  "SAML2Core 2.7.2 recommends two anti-correlation schemes: (a) small positive integers or repeated constants, and (b) the enclosing assertion ID. The SHOULD rules about value-space density and random selection are internal to scheme (a) and do not apply to scheme (b). Evidence is directional: a target-emitted SessionIndex drawn from a small integer set and unequal to the assertion ID demonstrates scheme (a)."),
 ("uses_random_identifier_generation","CAPABILITY_BASED","Whether the target uses a random or pseudorandom identifier-generation technique",
  ["declared_features.random_id_generation"],
  ["target_emitted_identifiers_are_high_entropy: true"],
  "The collision-probability and seed clauses in SAML2Core 1.3.4 are scoped by 'In the case that a random or pseudorandom technique is employed'. They do not apply to sequential, hash-derived, or other non-random schemes. The unconditional negligible-probability and exactly-one-declaration rules still apply to non-random schemes through IIP-SSO01.af/.ao and .cc."),
 ("supports_authnrequest_proxying","CAPABILITY_BASED","Whether the target acts as a proxying identity provider for AuthnRequest messages",
  ["declared_features.authnrequest_proxying"],
  ["target_emitted_authnrequest_to_upstream_idp: true",
   "target_emitted: saml:AuthenticatingAuthority"],
  "Every normative clause in SAML2Core 3.4.1.5.1 presupposes proxying. Because 'An identity provider MAY proxy an <AuthnRequest>', proxying itself is optional and the rules are not applicable to a non-proxying target. Evidence is directional: only an AuthnRequest emitted by the target to an upstream IdP, or an AuthenticatingAuthority emitted in the target's assertion, demonstrates the capability."),
 ("emits_idplist_getcomplete","CAPABILITY_BASED","Whether the target emits samlp:IDPList/samlp:GetComplete",
  ["declared_features.idplist_getcomplete"],
  ["target_emitted: samlp:GetComplete"],
  "The SAML2Core 3.4.1.3 clause 'Retrieving the resource associated with the URI MUST result in ...' governs the resource supplied by the emitter of GetComplete. It is not applicable when the target does not emit GetComplete."),
 ("unsolicited_acs_from_metadata","CAPABILITY_BASED","Whether the target emits unsolicited Responses and derives their ACS destination from metadata",
  ["declared_features.idp_initiated_sso","declared_features.metadata_driven_acs"],
  ["target_emitted_unsolicited_response: true",
   "target_used_metadata_acs_for_unsolicited: true"],
  "The SAML2Prof 4.1.5 recommendation is scoped by two conjunctive conditions: the target emits an unsolicited response and it uses metadata to determine the ACS. Neither condition alone is sufficient. An IdP that emits unsolicited responses but selects the ACS without metadata is outside this SHOULD. Evidence is directional: the target must emit the unsolicited Response and its destination must match the metadata ACS."),
 ("derives_url_from_relaystate","CAPABILITY_BASED","Whether the target derives a navigation URL from RelayState",
  ["declared_features.relaystate_as_url"],
  ["target_redirected_user_agent_to_relaystate_value: true"],
  "The new SAMLProf 4.1.6 added by SAML2Errata E90 says 'The URL scheme eventually derived SHOULD be limited to \"https\" or \"http\"'. This constrains implementations only when they derive a URL. Treating RelayState as an opaque token or rejecting every absolute URL is conforming, so the obligation is not applicable when no URL is derived. Evidence is directional: only navigation of the user agent to the RelayState value demonstrates the behavior."),
 ("allowcreate_general_interoperability_case","CLASSIFICATION_BASED",
  "Whether the SP does not use AllowCreate for a specific state-management purpose and treats it as a general interoperability default",
  ["declared_features.allowcreate_specific_use"],[],
  "The SAML2Errata E14 SHOULD is scoped to 'Requesters that do not make specific use of this attribute'. It does not apply when the target uses AllowCreate for consent, dynamic identifier creation, Name Identifier Management, or another specific purpose. The absence of such a purpose cannot be established from protocol traffic, so false requires an explicit declared exclusion with a reason.",
  "The target was declared to make specific use of AllowCreate for state-management semantics, so the general interoperability recommendation does not apply. This was not verified by the Suite."),
 ("proxy_allowcreate_general_interoperability_case","CLASSIFICATION_BASED",
  "Whether the target proxies AuthnRequest messages and does not use AllowCreate for a specific state-management purpose",
  ["declared_features.authnrequest_proxying","declared_features.allowcreate_specific_use"],
  [],
  "The E14 SHOULD for a proxy IdP has two conjunctive conditions: the target performs proxying and does not make specific use of AllowCreate. Observing an upstream request proves only proxying, not the absence of a specific purpose, so it is not used as observed evidence. If either condition is false the obligation is not applicable; negating the purpose requires a reasoned declaration because it is not observable on the protocol surface.",
  "The target was declared either not to proxy AuthnRequest messages or to make specific use of AllowCreate for state-management semantics, so the general interoperability recommendation does not apply. This was not verified by the Suite."),
 ("relaystate_privacy_required","CLASSIFICATION_BASED","Whether this deployment requires privacy protection for RelayState",
  ["target.relaystate_privacy"],[],
  "SAML2Prof 4.1.3.1 says 'unless the use of the profile does not require such privacy measures'. This is an explicit source exclusion, but only the deployment can establish its basis. The default is true; false requires an explicit declared exclusion with a reason, and the exclusion is surfaced at the top level of the Run result.",
  "The deployment declared that the use of the profile does not require privacy measures for RelayState, "
  "as permitted by [SAML2Prof] section 4.1.3.1. This was not verified by the Suite."),
 ("slo_relaystate_privacy_required","CLASSIFICATION_BASED","Whether this deployment requires RelayState privacy protection for Single Logout",
  ["target.slo_relaystate_privacy"],[],
  "SAML2Prof 4.4.3.1 says 'unless the use of the profile does not require such privacy measures'. This predicate is separate because the deployment's privacy requirement may differ between Web Browser SSO and Single Logout. False requires an explicit declared exclusion with a reason, surfaced at the top level of the Run result.",
  "The deployment declared that use of the Single Logout profile does not require privacy measures for RelayState, "
  "as permitted by [SAML2Prof] section 4.4.3.1. This was not verified by the Suite."),
 ("supports_unsolicited_responses","CAPABILITY_BASED","Whether the IdP emits unsolicited Responses for IdP-initiated SSO",
  ["declared_features.idp_initiated_sso"],
  ["target_emitted_unsolicited_response: true"],
  "SAML2Prof 4.1.5 says an identity provider MAY initiate the profile with an unsolicited Response. Non-emission is therefore not a violation; unsolicited-specific obligations are not applicable. Only a Response emitted by the target without an AuthnRequest is directional evidence."),
 ("supports_slo_idp","CAPABILITY_BASED","Whether the IdP supports the SLO profile",
  ["declared_features.single_logout"],
  ["target_metadata_has: md:IDPSSODescriptor/md:SingleLogoutService",
   "target_emitted: samlp:LogoutRequest",
   "target_consumed: samlp:LogoutRequest"],
  "SAML2Prof 4.1.4.2 as amended by E26 says that when the identity provider supports Single Logout, authentication statements MUST include SessionIndex. Although IIP-IDP17 independently requires SLO support, this predicate preserves the source condition and avoids reporting a second SessionIndex failure when the target already violates IIP-IDP17."),
 ("supports_artifact_binding","CAPABILITY_BASED","Whether the target supports the HTTP Artifact binding",
  ["declared_features.artifact_binding"],
  ["target_metadata_has: md:ArtifactResolutionService",
   "target_emitted: samlp:ArtifactResolve",
   "target_consumed: samlp:ArtifactResolve"],
  "SAML2Prof 4.1.4.4 is scoped by 'If the HTTP Artifact binding is used to deliver the <Response>'. IIP-SSO02 and IIP-SSO03 require only Redirect and POST, so lack of Artifact support is not a violation."),
 ("supports_encrypted_nameid","CAPABILITY_BASED","Whether the target can emit encrypted name identifiers as saml:EncryptedID",
  ["declared_features.encrypted_nameid"],
  ["target_emitted: saml:EncryptedID"],
  "Format=...:encrypted in SAML2Core 3.4.1.1 requires EncryptedID in the resulting assertion. IIP-IDP09.b makes identifier encryption OPTIONAL, so lack of support is not applicable rather than a violation. Evidence is directional: only EncryptedID emitted by the target demonstrates the capability."),
 ("reissues_foreign_persistent_identifier","CAPABILITY_BASED","Whether the target reissues a persistent identifier generated by another entity",
  ["declared_features.proxy_idp"],
  ["target_reissued_upstream_persistent_nameid: true"],
  "Condition for IIP-SSO05.a6/.a7. The reissuance rules in SAML2Core 8.3.7 apply only when 'a different system entity might later issue its own protocol message or assertion containing the identifier'. Evidence is directional: the target must emit in its own Assertion the same NameID value that the upstream Samlier IdP emitted. Merely claiming to be a proxy is not a capability observation."),
]
L=["# tests/predicates.yaml — canonical G1 applicability predicates",
   "# Generated by tools/g1_author.py. Do not edit manually.",
   f"generated_at: {NOW}","schema_version: 2",
   "# CLAIM_BASED: the declaration itself determines applicability; observed evidence is unnecessary.",
   "# CAPABILITY_BASED: actual capability determines applicability; declaration-only false remains UNKNOWN.",
   "# CLASSIFICATION_BASED: only declaration_only_exclusion may make the classification false.",
   "predicates:"]
for _p in PRED:
    name,kind,desc,decl,obs,note = _p[:6]
    excl = _p[6] if len(_p)>6 else None
    assert (kind=="CLASSIFICATION_BASED")==(excl is not None), \
        f"{name}: CLASSIFICATION_BASED requires declaration_only_exclusion.statement_en; other kinds must not have it"
    L+=[f"  {name}:",f"    kind: {kind}",f"    description_en: {y(desc)}"]
    L.append("    declared:"+(" []" if not decl else ""))
    for d in decl: L.append(f"      - {y(d)}")
    L.append("    observed:"+(" []" if not obs else ""))
    for o in obs: L.append(f"      - {y(o)}")
    L.append("    on_conflict: inconsistent")
    if kind=="CLASSIFICATION_BASED":
        L+=["    declaration_only_exclusion:","      allowed: true","      requires_reason: true",
            f"      statement_en: {yq(excl)}"]
    L.append(f"    rationale_en: {y(note)}")
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
# Allowed linked-obligation kinds. Any extension must update the semantic
# definition in docs/03 and SR-22g in g1_validate.py at the same time.
LINK_KINDS = {'inherit_variants'}

def level_assignment(rid,o):
    """Samlier-specific Core: MUST_CLASS excluding SLO, ECP, and Discovery."""
    sec=SECTION_OF[rid][0]
    slo_ecp = sec in ('3.2','4.2','4.3') or rid in ('IIP-SP04',)
    core = (o['level'] in MUSTC) and not slo_ecp
    return {r:('core' if core else 'full') for r in o['roles']}

L=["# tests/coverage.yaml — canonical G1 requirements catalog and sole source of verdict levels",
   "# Generated by tools/g1_author.py after resolving source offsets and digests. Do not edit manually.",
   f"generated_at: {NOW}",
   "schema_version: 2",
   "g1_state: PENDING_REVIEW",
   "# Authors leave reviewer and approved_at empty. An independent reviewer must compare the catalog directly with the source.",
   "spec: kantara-fedinterop-impl",
   "spec_version: \"1.1\"",
   "source_digest: "+yq(SRC_SHA),
   "catalog_digest: null   # Filled after generation from the complete specs.yaml and predicates.yaml documents.",
   "# Approval evidence is external to the target commit in tests/approvals/g1.yaml.",
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
        if cond and cond['kind']=='CLASSIFICATION_BASED':
            # Store the exact source sentence that creates the exclusion.
            # SR-14 verifies it against either the IIP clause or reference text.
            if not o.get('exclusion_clause_en'):
                raise SystemExit(f"{o['key']}: a CLASSIFICATION_BASED condition requires exclusion_clause_en")
            L.append(f"        exclusion_clause_en: {y(o['exclusion_clause_en'])}")
        elif o.get('exclusion_clause_en'):
            raise SystemExit(f"{o['key']}: exclusion_clause_en is valid only with a CLASSIFICATION_BASED condition")
        if o['testability']=='CONFIG':
            assert o.get('config_semantics'), f"{o['key']}: CONFIG requires explicit configuration_failure_semantics"
        if o.get('config_semantics'):
            L.append(f"        configuration_failure_semantics: {o['config_semantics']}")
        L.append(f"        summary_en: {y(o['summary_en'])}")
        if o.get('spec_item'):
            if 'reference_derivation' not in o:
                raise SystemExit(f"{o['key']}: obligations with references_spec must explicitly set reference_derivation")
            rd=o['reference_derivation']
            if rd is True and not o.get('reference_evidence'):
                raise SystemExit(f"{o['key']}: reference_derivation=True requires reference_evidence")
            if rd is False and o.get('reference_evidence'):
                raise SystemExit(f"{o['key']}: reference_derivation=False must not include reference_evidence")
            if rd is False and not o.get('reference_derivation_note'):
                raise SystemExit(f"{o['key']}: reference_derivation=False requires reference_derivation_note explaining why the cited specification is not verdict evidence")
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
                     f"            basis_en: {y(ev['basis_en'])}"]
        if o.get('applicability_note_en'):
            L.append(f"        applicability_note_en: {y(o['applicability_note_en'])}")
        la=level_assignment(rid,o)
        L.append("        level_assignment: { "+", ".join(f"{k}: {v}" for k,v in la.items())+" }")
        L.append("        source_clauses:")
        for cc in o['_clauses']:
            L.append(f"          - {{ start: {cc['start']}, end: {cc['end']}, digest: {yq(cc['digest'])}, occurrences: {cc['occurrences']} }}")
        if o['testability']=='NOT_OBSERVABLE':
            L.append(f"        not_observable_reason_en: {y(o['not_observable_reason_en'])}")
            L.append("        required_variants: []")
        else:
            L.append("        required_variants:")
            for v in o.get('variants',[]):
                # Stable ID derived from the English description. Include the
                # obligation key to prevent cross-obligation collisions.
                vid='v-'+hashlib.sha256((o['key']+'\x00'+v).encode('utf-8')).hexdigest()[:10]
                L.append(f"          - id: {vid}")
                L.append(f"            description_en: {y(v)}")
        if o.get('linked_obligations'):
            L.append("        linked_obligations:")
            for lk in o['linked_obligations']:
                if lk.get('kind') not in LINK_KINDS:
                    raise SystemExit(f"{o['key']}: linked_obligations.kind must be one of {sorted(LINK_KINDS)}; got {lk.get('kind')!r}. Define new kinds in docs/03 and g1_validate.py SR-22g first")
                if not lk.get('note_en'):
                    raise SystemExit(f"{o['key']}: linked_obligations requires note_en explaining the inherited content")
                L+= [f"          - obligation: {lk['obligation']}",
                     f"            kind: {lk['kind']}",
                     f"            note_en: {y(lk['note_en'])}"]
        if o.get('controls'):
            L.append("        controls:")
            for v in o['controls']: L.append(f"          - {y(v)}")
        if o.get('notes_en'): L.append(f"        notes_en: {y(o['notes_en'])}")
        if o.get('open_question'): L.append(f"        open_question_en: {y(o['open_question'])}")
        L+= ["        authored_by: samlier-g1-builder",
             "        review:",
             "          # Authoring state only. Canonical approval evidence is in tests/approvals/g1.yaml.",
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
# Re-read the emitted YAML and compute obligation digests from the same values
# visible to the validator.
try:
    import yaml as _yaml
except ImportError:
    raise SystemExit("PyYAML is required: .venv/bin/pip install -r tools/requirements.lock")
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
# Record the digest of the complete specs and predicates catalogs.
_specs=_yaml.safe_load(open(os.path.join(TESTS,'specs.yaml'),encoding='utf-8'))
_cat=X.catalog_digest(_specs,{'predicates':_preds})
_txt=open(_path,encoding='utf-8').read().replace('catalog_digest: null','catalog_digest: "%s"'%_cat,1)
open(_path,'w',encoding='utf-8').write(_txt)
print("filled obligation_digest:",_i,"/ catalog_digest:",_cat[:22])


print("done; next run tools/g1_docgen.py and tools/g1_validate.py")
