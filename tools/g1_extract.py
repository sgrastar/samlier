#!/usr/bin/env python3
"""g1_extract.py — Fetch and deterministically normalize and extract sections from source documents (shared by author and validator)

Keeping normalization in one place ensures that generation and validation produce the same string.
"""
import os,re,html,hashlib,unicodedata,urllib.request

CACHE_DIRNAME=os.path.join('build','spec-cache')

def cache_dir(root):
    d=os.path.join(root,CACHE_DIRNAME); os.makedirs(d,exist_ok=True); return d

def fetch(root,key,url,mode='network',timeout=60):
    """Fetch the source document.

    mode='network' (default): **Always refetch from the network** and update the cache.
        This prevents a stale cache from producing PASS when the URL is unreachable.
    mode='offline'       : Cache only; returns (None, path) if uncached.
    mode='cache-first'   : For authoring; uses the cache when available.
    """
    assert mode in ('network','offline','cache-first'), mode
    u=url.lower()
    ext=('.pdf' if u.endswith('.pdf') else
         '.txt' if u.endswith('.txt') else
         '.xml' if u.endswith(('.xsd','.xml')) else '.html')
    path=os.path.join(cache_dir(root),key+ext)
    if mode in ('offline','cache-first') and os.path.exists(path):
        return open(path,'rb').read(),path
    if mode=='offline':
        return None,path
    req=urllib.request.Request(url,headers={'User-Agent':'samlier-g1/1.0'})
    data=urllib.request.urlopen(req,timeout=timeout).read()
    open(path,'wb').write(data)
    return data,path

# ---- Canonical form of the obligation content covered by approval ----
# The approval digest covers every field except "review".
# Enumerating fields can miss newly added fields (human-readable explanatory fields were once
# omitted, so changing them after approval went undetected).
EXCLUDED_FROM_DIGEST=('review',)

def _norm(v):
    """Convert to a JSON-compatible canonical form; date / datetime use ISO strings."""
    import datetime as _dt
    if isinstance(v,dict):  return {str(k):_norm(v[k]) for k in sorted(v,key=str)}
    if isinstance(v,list):  return [_norm(x) for x in v]
    if isinstance(v,(_dt.date,_dt.datetime)): return v.isoformat()
    return v

def canonical_obligation(o,predicates=None):
    """Canonical form of an obligation (sorted JSON).

    - Includes every field except review, including human-readable text emitted in docs/04.
    - Embeds the **definition itself** of the referenced predicate. Including only its name
      would leave the digest unchanged if predicates.yaml's declared / observed / exclusion
      rules changed.
    """
    import json
    d={k:_norm(v) for k,v in o.items() if k not in EXCLUDED_FROM_DIGEST and v is not None}
    cond=o.get('condition') or {}
    pname=cond.get('predicate')
    if pname is not None:
        defn=(predicates or {}).get(pname)
        d['_predicate_definition']=_norm(defn) if defn is not None else '<MISSING>'
    return json.dumps(d,ensure_ascii=False,sort_keys=True,separators=(',',':'))

def obligation_digest(o,predicates=None):
    return sha(canonical_obligation(o,predicates))

def catalog_digest(specs_doc,predicates_doc):
    """Digest the complete specs.yaml and predicates.yaml documents (complements obligation digests)."""
    import json
    return sha(json.dumps({'specs':_norm(specs_doc.get('specs')),
                           'predicates':_norm(predicates_doc.get('predicates'))},
                          ensure_ascii=False,sort_keys=True,separators=(',',':')))

def sha(b):
    if isinstance(b,str): b=b.encode('utf-8')
    return 'sha256:'+hashlib.sha256(b).hexdigest()

def normalize_html(raw, mark_em=False):
    t=raw.decode('utf-8',errors='replace')
    t=re.sub(r'(?is)<(script|style).*?</\1>','',t)
    if mark_em:
        t=t.replace('<em>','\x01').replace('</em>','\x02')
        t=re.sub(r'(?i)<em [^>]*>','\x01',t)
    t=re.sub(r'(?i)<(/?)(p|div|li|ul|ol|table|tr|td|th|h[1-6]|dt|dd|pre|br)[^>]*>','\n',t)
    t=re.sub(r'(?s)<[^>]+>','',t)
    t=html.unescape(t); t=unicodedata.normalize('NFC',t)
    return '\n'.join(l for l in (re.sub(r'[ \t]+',' ',x).strip() for x in t.split('\n')) if l)

def normalize_pdf(raw):
    """Extract text with pdfminer.six and normalize it by removing page footers and line-number columns."""
    from pdfminer.high_level import extract_text
    import io
    t=extract_text(io.BytesIO(raw))
    t=unicodedata.normalize('NFC',t)
    t=re.sub(r'\n[a-z0-9-]+-2\.0-os\s*\n.*?Page \d+ of \d+\s*\n','\n',t,flags=re.S)   # OASIS footer
    t=re.sub(r'\n(?:\s*\d{1,4}\s*\n)+','\n',t)                                        # Line-number column
    return '\n'.join(l for l in (re.sub(r'[ \t]+',' ',x).strip() for x in t.split('\n')) if l)

def normalize_text(raw):
    """For text/plain documents (such as IETF drafts). ★ Preserve angle brackets.

    Processing as HTML would remove **XML element names in the specification text**, such as
    <samlec:GeneratedKey>, as tags and break both evidence digests and term checks.
    """
    t=raw.decode('utf-8',errors='replace')
    t=unicodedata.normalize('NFC',t)
    t=re.sub(r'\n\f?[^\n]*\[Page \d+\][^\n]*\n','\n',t)      # RFC page footer
    t=re.sub(r'\n\f[^\n]*\n','\n',t)                          # Header immediately after a page break
    return '\n'.join(l for l in (re.sub(r'[ \t]+',' ',x).rstrip() for x in t.split('\n')) if l)

def normalize(raw,url,mark_em=False):
    u=url.lower()
    if u.endswith('.pdf'):  return normalize_pdf(raw)
    # ★ Preserve angle brackets in .txt / .xsd / .xml files.
    #   Processing them as HTML would remove XML element names and schema definitions.
    if u.endswith(('.txt','.xsd','.xml')):  return normalize_text(raw)
    return normalize_html(raw,mark_em)

LOCATOR_SEP='||'
def section(text,locator):
    """Extract a section with locator = '<start-heading regex>||<next-heading regex>'.
       Use the last start match (to avoid duplicate table-of-contents entries) and the first
       subsequent end match."""
    start_re,end_re=locator.split(LOCATOR_SEP,1)
    st=[m.start() for m in re.finditer(start_re,text)]
    if not st: raise KeyError(f'locator start not found: {start_re!r}')
    i=st[-1]
    en=[m.start() for m in re.finditer(end_re,text) if m.start()>i]
    if not en: raise KeyError(f'locator end not found after start: {end_re!r}')
    return text[i:en[0]].strip()
