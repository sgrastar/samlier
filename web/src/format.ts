const acronyms: Record<string, string> = {
  api: 'API',
  ecp: 'ECP',
  http: 'HTTP',
  idp: 'IdP',
  mdq: 'MDQ',
  saml: 'SAML',
  slo: 'SLO',
  sp: 'SP',
  sso: 'SSO',
  url: 'URL',
  xml: 'XML',
}

export function humanize(value: string) {
  const rendered = value.toLowerCase().split(/[_-]/).map(word => acronyms[word] ?? word).join(' ')
  return rendered.replace(/^./, first => first.toUpperCase())
}
