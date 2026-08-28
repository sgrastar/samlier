# 08. Security of the Suite Itself

This Suite is inherently **“a tool that sends arbitrary HTTP requests to arbitrary URLs and generates arbitrary XML.”**
Because it will be exposed as a Hosted version, address at the design stage the fact that the Suite itself could become an attack platform.
This area was entirely absent from the original memo.

## 1. SSRF (The Greatest Risk)

Users can enter arbitrary URLs in a Test Plan. The Suite makes back-channel connections to them.

```
Attacker ──▶ Hosted Suite ──▶ http://169.254.169.254/latest/meta-data/  (cloud metadata)
                       ──▶ http://10.0.0.5:6379/                      (internal Redis)
                       ──▶ http://localhost:8080/api/                 (the Suite's own API)
```

Moreover, because the Transcript retains the full response, **the retrieved content is visible to the user as-is**.
This is a typical non-blind SSRF.

### Countermeasures

| Countermeasure | Details |
|---|---|
| Outbound destination filtering | In `hosted` mode, **reject the connection** if the IP after name resolution is private / loopback / link-local / CGNAT / multicast / IPv6 ULA |
| DNS rebinding protection | Pin the result of name resolution before connecting (connect directly to the resolved IP and specify the `Host` header). Do not resolve again between resolution, checking, and connection |
| Redirect following | Perform the same inspection at each hop. Maximum 3 hops |
| Scheme restrictions | Only `http` / `https`. Reject `file:` `gopher:` `ftp:` `jar:` |
| Port restrictions | In `hosted`, use an allowlist such as 80 / 443 / 8080 / 8443 |
| Response size limits | Metadata 5 MB, other responses 1 MB |
| Timeouts | Connection 5 seconds / total 30 seconds |
| Prohibit access to the Suite's own API | Explicitly block its own base URL / internal ports |
| self-hosted default | `SAMLIER_OUTBOUND_ALLOW_PRIVATE=true` (allow it because testing internal IdPs is a primary purpose). **State this difference explicitly in the README** |

> The key point is to use different defaults for `hosted` and `selfhosted`.
> The reason self-hosted exists is to enable testing internal IdPs, so do not restrict it there.

## 2. XXE / XML Bombs (Against the Suite Itself)

The Suite parses XML received from the Target. **The Suite side is also an attack target.**

- For every `DocumentBuilderFactory` / `XMLInputFactory` / `TransformerFactory`, set
  `FEATURE_SECURE_PROCESSING=true` and disable external general entities, external parameter entities, and DTDs
- However, **IIP-G03 requires generating XML containing a DTD**.
  Separate the settings for the generation path (`raw/`) and parsing path (`normal/`). Separate the packages so they cannot be confused
- Set limits on entity expansion count, nesting depth, and element count (billion laughs)
- Limit the size of received XML
- **Reject signatures containing an XSLT Transform during verification** (XSLT in signature verification is an attack surface)

## 3. Implementation Notes for XML Signature Verification

The Suite also determines “whether the other party’s signature is correct.” Getting this wrong distributes incorrect determinations.

- Always verify that the `Reference` URI **actually points to the element covered by the signature** (XSW protection)
- Limit permitted Transforms to **Enveloped Signature + C14N only**
- Detect duplicate ID attributes
- Use not only “verification passed” but also **“what was signed”** in the determination
- Do not rely entirely on OpenSAML’s `SignatureValidator`; record the Reference URI / Transform / key used / XPath of the signed element in the Transcript

## 4. Test Peer Key Management

- Generate a key pair for each Test Plan (do not share them)
- **State explicitly in the README that private keys are stored in plaintext under `/data`**. The Suite is not for production use
- Use a Subject DN that makes clear that generated keys are “for testing only”
  (example: `CN=samlier test key (DO NOT TRUST), OU=Test Plan 01K3..., O=samlier`)
- Delete Hosted-version keys after the Run retention period expires
- **Do not persist HTTP Basic credentials for the ECP test (IIP-IDP14)**.
  Keep them in memory only while running, and do not write them to `CaseState` or the Transcript.
  Irreversibly remove the `Authorization` header at the Recorder entry point ([02 §5.2](02-architecture.md)).
  Use `RedactorTest` to verify that “after an ECP round trip with Basic authentication, the credentials do not appear in any byte under `/data`”
- **Do not distribute known fixed keys** (distribution would enable attacks against implementations that trust those keys)

## 5. Open Redirect / Reflected XSS

The Suite sometimes redirects the browser to a URL in a SAML message (such as the ACS in an SP test).

- Limit redirect destinations **to URLs listed in the Target metadata**
- If a test exceptionally redirects to an arbitrary URL, insert an intermediate confirmation page
- When displaying Transcript XML in the UI, **always escape it**.
  XML from the Target may contain arbitrary scripts
- Because `report.html` is distributed as a self-contained file, be especially strict about escaping embedded Target-derived data
### ★ Origin separation is a requirement, not something to “consider”

The Test Peer’s job is to **receive and observe even invalid Assertions**, and its validation is intentionally relaxed.
The `app` side, meanwhile, has sessions tied to administrative tokens ([09 D-09](09-open-decisions.md)).
If they share an origin, Target-derived content reaching the Test Peer could interact with the administrative session.

| Deployment mode | Normative level |
|---|---|
| **Hosted** | **MUST**. Use separate origins for `app.<domain>` and `peer.<domain>`. Reject startup for configurations that cannot separate them |
| **self-hosted (publicly exposed to the Internet)** | **SHOULD**. Strongly recommend setting `SAMLIER_PEER_BASE_URL`, and issue a startup warning if it is unset |
| **self-hosted (closed network)** | **MAY**. The same origin is acceptable (the administrative token is effectively meaningless) |

When `SAMLIER_MODE=hosted` and `SAMLIER_PEER_BASE_URL` has the same origin as
`SAMLIER_PUBLIC_BASE_URL`, make startup **fail with an error**.

### CSP for the administrative UI

```
Content-Security-Policy:
  default-src 'none';
  script-src 'self' 'nonce-{per-response-random}';
  style-src  'self' 'nonce-{per-response-random}';
  connect-src 'self';
  img-src    'self' data:;
  form-action 'self';
  frame-ancestors 'none';
  base-uri   'none';
  object-src 'none'
```

- Generate a **new random value of at least 128 bits for each response** for `nonce`.
  Do not use a fixed value or build-time constant (that would not stop XSS)
- Do not use `'unsafe-inline'` / `'unsafe-eval'` / `'strict-dynamic'`
- Do not place any externally sourced resources (images, scripts, iframes, or fonts) in the administrative UI
- Apply a separate (it may be more permissive) CSP to the `peer` origin. Do not share it

## 6. Abuse Prevention for the Hosted Version

| Risk | Countermeasure |
|---|---|
| A platform for scanning or DoS attacks against someone else’s IdP / SP | Rate limiting, limiting concurrent Runs against the same Target, and a confirmation checkbox when creating a Test Plan stating that the user has authority to operate the test target |
| Mass creation of Test Plans | Per-IP / per-account limits |
| Reputation manipulation of another company’s product using public results | Explicitly state that the product name is self-declared. **Provide a channel for deletion requests**. Include it in the Hosted version’s terms of use |
| Disclosure of real users’ personal information | Default masking in [06](06-results-and-publication.md) + preview before publication |

## 7. Notes for Phase 4

In Phase 4, the Suite will become “a tool that generates attack SAML messages.”

- **Do not provide generated attack messages as a general-purpose tool that can be sent directly to third parties**
  (send them only to the Target specified in the Test Plan)
- State in the README that it must be used only against systems the user has authority to operate
- However, because it is OSS, anyone can modify the code. Excessive restrictions have little meaning, so
  **draw the line through the terms of use and design defaults**
