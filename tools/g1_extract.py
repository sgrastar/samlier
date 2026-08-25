#!/usr/bin/env python3
"""g1_extract.py — 原文の取得と決定論的な正規化・節切り出し（author と validator の共有モジュール）

正規化手順を 1 箇所に置くことで、生成側と検証側が同じ文字列に到達することを保証する。
"""
import os,re,html,hashlib,unicodedata,urllib.request

CACHE_DIRNAME=os.path.join('build','spec-cache')

def cache_dir(root):
    d=os.path.join(root,CACHE_DIRNAME); os.makedirs(d,exist_ok=True); return d

def fetch(root,key,url,mode='network',timeout=60):
    """原文を取得する。

    mode='network' (既定): **必ずネットワークから再取得**しキャッシュを更新する。
        キャッシュが古い / URL が到達不能でも古い内容で PASS してしまう事故を防ぐ。
    mode='offline'       : キャッシュのみ。未キャッシュなら (None, path)。
    mode='cache-first'   : 起票（author）用。キャッシュがあればそれを使う。
    """
    assert mode in ('network','offline','cache-first'), mode
    ext='.pdf' if url.lower().endswith('.pdf') else ('.txt' if url.lower().endswith('.txt') else '.html')
    path=os.path.join(cache_dir(root),key+ext)
    if mode in ('offline','cache-first') and os.path.exists(path):
        return open(path,'rb').read(),path
    if mode=='offline':
        return None,path
    req=urllib.request.Request(url,headers={'User-Agent':'samlier-g1/1.0'})
    data=urllib.request.urlopen(req,timeout=timeout).read()
    open(path,'wb').write(data)
    return data,path

# ---- 承認の対象となる「義務の内容」の正規形 ----
# 承認 digest の対象は「review 以外の全フィールド」。
# 列挙方式にすると新しいフィールドを足したときに取りこぼす（実際に summary_ja / notes_ja が
# 漏れており、承認後に日本語の説明を書き換えても検出できなかった）。
EXCLUDED_FROM_DIGEST=('review',)

def _norm(v):
    """JSON 化できる正規形にする。date / datetime は ISO 文字列にする。"""
    import datetime as _dt
    if isinstance(v,dict):  return {str(k):_norm(v[k]) for k in sorted(v,key=str)}
    if isinstance(v,list):  return [_norm(x) for x in v]
    if isinstance(v,(_dt.date,_dt.datetime)): return v.isoformat()
    return v

def canonical_obligation(o,predicates=None):
    """義務の正規形（JSON, ソート済み）。

    - review 以外の全フィールドを含む（生成物 docs/04 に出る日本語も含む）
    - 参照する述語の**定義そのもの**を埋め込む。述語名だけだと predicates.yaml の
      declared / observed / 除外規則を書き換えても digest が変わらない
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
    """specs.yaml と predicates.yaml 全体の digest（義務単位の digest を補う）。"""
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
    """pdfminer.six でテキスト化し、ページフッタと行番号列を除いて正規化する。"""
    from pdfminer.high_level import extract_text
    import io
    t=extract_text(io.BytesIO(raw))
    t=unicodedata.normalize('NFC',t)
    t=re.sub(r'\n[a-z0-9-]+-2\.0-os\s*\n.*?Page \d+ of \d+\s*\n','\n',t,flags=re.S)   # OASIS フッタ
    t=re.sub(r'\n(?:\s*\d{1,4}\s*\n)+','\n',t)                                        # 行番号列
    return '\n'.join(l for l in (re.sub(r'[ \t]+',' ',x).strip() for x in t.split('\n')) if l)

def normalize(raw,url,mark_em=False):
    return normalize_pdf(raw) if url.lower().endswith('.pdf') else normalize_html(raw,mark_em)

LOCATOR_SEP='||'
def section(text,locator):
    """locator = '<開始見出しの正規表現>||<次の見出しの正規表現>' で節を切り出す。
       開始は最後の一致（目次の重複を避ける）、終了はそれ以降の最初の一致。"""
    start_re,end_re=locator.split(LOCATOR_SEP,1)
    st=[m.start() for m in re.finditer(start_re,text)]
    if not st: raise KeyError(f'locator start not found: {start_re!r}')
    i=st[-1]
    en=[m.start() for m in re.finditer(end_re,text) if m.start()>i]
    if not en: raise KeyError(f'locator end not found after start: {end_re!r}')
    return text[i:en[0]].strip()
