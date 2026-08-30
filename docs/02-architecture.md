# 02. Architecture

## 1. Overall Structure

```
┌───────────────────────────────────────────────────────────────┐
│ User's browser                                                │
│   (a) Has the Suite Web UI open                               │
│   (b) Is also the SAML user agent at the same time ★          │
└──────┬─────────────────────────────┬──────────────────────────┘
       │ REST + SSE                  │ SAML front-channel
       │                             │ (HTTP-Redirect / HTTP-POST)
       ▼                             ▼
┌───────────────────────────────────────────────────────────────┐
│ samlier  (single JVM / single container)                     │
│                                                               │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Web API     │  │ Test Runner  │  │ Protocol Endpoints   │  │
│  │ + UI        │──│ (state       │──│  /p/{plan}/sp/acs    │  │
│  │ delivery    │  │ machine)     │  │  /p/{plan}/sp/slo    │  │
│  └─────────────┘  └──────┬───────┘  │  /p/{plan}/idp/sso   │  │
│                          │          │  /p/{plan}/idp/slo   │  │
│  ┌───────────────────────┴───────┐  │  /p/{plan}/metadata  │  │
│  │ SAML Engine                   │  │  /mdq/{entityID}     │  │
│  │  ├ OpenSAML 5  (normal path)  │  └──────────────────────┘  │
│  │  ├ Santuario   (XML signature │                           │
│  │  │               /encryption) │  ┌──────────────────────┐  │
│  │  └ Raw DOM/StAX (abnormal     │  │ Transcript Recorder  │  │
│  │                 path) ★       │  │ Records all HTTP +  │  │
│  └───────────────────────────────┘  │ all SAML messages   │  │
│                                     └──────────────────────┘  │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Test Defs   │  │ Key Store    │  │ Store (SQLite)       │  │
│  │ (YAML,      │  │ (keys per    │  └──────────────────────┘  │
│  │ embedded)   │  │ plan)        │                           │
│  └─────────────┘  └──────────────┘                           │
└──────┬────────────────────────────────────────────────────────┘
       │ back-channel (Suite → Target)
       │  - Retrieval of Target metadata / MDQ
       │  - SOAP SLO, ECP
       ▼
   Target IdP / Target SP
```

The two points marked ★ are specific to SAML and have the greatest architectural impact.

- The **browser is part of the test path**. The Suite cannot log directly into the target (and should not hold the user's credentials).
- **OpenSAML is insufficient for producing abnormal XML**. With Phase 4 in mind, a low-level XML generation path must be separated from the beginning.

## 2. Technology Stack

| Layer | Choice | Status |
|---|---|---|
| Language / runtime | Java 21 (LTS) | Decided |
| SAML | OpenSAML 5.x (Java 17+ / Apache-2.0) | Decided |
| XML Security | Apache Santuario XML Security for Java | Decided |
| Low-level XML | JDK standard DOM / StAX + string templates | Decided |
| Web framework | **Javalin + Jetty** | Decided (access to the raw query string is required. §3.5) |
| DB | SQLite (xerial sqlite-jdbc), keep the access layer thin | Provisionally decided |
| Frontend | **React + Vite (TypeScript)** | Decided |
| Distribution | Docker (multi-architecture: amd64 / arm64) | Decided |
| Build | **Gradle (Kotlin DSL)** | Decided |

### Policy of avoiding excessive dependence on OpenSAML

```
                 ┌──────────────────────────┐
   Normal    ────│ MessageFactory (OpenSAML)│──┐
                 └──────────────────────────┘  │   ┌──────────────┐
                                               ├──▶│ Serializer   │──▶ wire
                 ┌──────────────────────────┐  │   │ (DOM → bytes)│
   Abnormal  ────│ RawMessageBuilder        │──┘   └──────────────┘
   (Phase 4)     │ (direct DOM manipulation / strings) │
                 └──────────────────────────┘
```

- The final stage of generation **must always reduce the result to DOM or raw bytes**. Do not use OpenSAML's object model as the final form.
- The receiving side follows the same rule: retain **both** the result parsed by OpenSAML and the DOM of the raw XML. (Information normalized or ignored by OpenSAML may be needed for the determination, such as comment truncation attacks.)
- Keep signatures directly callable through Santuario (an invalid signature cannot be created through OpenSAML's Signer path).

> The abnormal-path builder will not be used as of Phase 1, but **the interface must be split out in Phase 1**.
> Inserting it later would duplicate the generation paths and cause them to fail.

## 3. Test Peer Design ★ Core of this design

### Stable metadata lab

For metadata-consumer tests, each Run exposes one stable endpoint:

```text
/p/{plan}/metadata/live?run={run}
```

Runner selects a signed fixture behind this URL without changing the URL registered at the Target.
The selected fixture is persisted in Run context, and every fetch is recorded in Transcript with its
variant identifier. A fetch alone does not identify the caller or prove Target use. Variant endpoints
include the same Run and variant correlation so that later inbound SAML can demonstrate actual use
rather than mere retrieval.

The management API selects only the Suite fixture; it never configures the Target. In particular,
Samlier does not call Keycloak Admin API or any equivalent vendor interface. A Target is connected by
standard SAML metadata/MDQ or by an explicit one-time manual import.

Evidence-driven cases expose their observation progress at Run scope. After the operator has triggered
the target's normal refresh/re-import and attempted the correlated SAML flows, Runner can finish every
ready case in one operation. Runner resumes only cases whose approved implementation reports complete
protocol evidence; it never bulk-confirms the remaining configuration cases and never derives a target
violation from a missing observation.

### Problem: SAML has no dynamic client registration

The OIDC Conformance Suite can issue a new issuer / client for each test.
With SAML, **the target must be asked to register the metadata manually**.
If the entityID is changed for each test case, users would be forced to perform registration 50–80 times, and no one would use it.

### Solution: 1 Test Plan = 1 entityID = 1 "all-in-one metadata"

When a Test Plan is created, the Suite issues the following as one set.

```
entityID : https://<base>/p/{planId}
metadata : https://<base>/p/{planId}/metadata      (signed)
MDQ      : https://<base>/mdq/{urlencoded-entityID}
```

This metadata contains from the outset **every element required by all test cases** included in the Test Plan.

#### Metadata as the Test SP (when testing an IdP)

```xml
<SPSSODescriptor AuthnRequestsSigned="true" WantAssertionsSigned="true" ...>
  <!-- Multiple signing keys: used for IIP-MD07 / MD11 tests -->
  <KeyDescriptor use="signing">     <!-- Key A: default -->
  <KeyDescriptor use="signing">     <!-- Key B: rollover destination -->
  <KeyDescriptor use="encryption">  <!-- Key C -->
  <KeyDescriptor use="encryption">  <!-- Key D: decryption rollover -->
  <KeyDescriptor>                   <!-- Key E: no use attribute → IIP-MD11 -->

  <!-- Algorithm declarations: used for IIP-MD09 / MD10 tests -->
  <alg:DigestMethod Algorithm="...sha256"/>
  <alg:SigningMethod Algorithm="...rsa-sha256"/>

  <SingleLogoutService Binding="HTTP-Redirect" .../>
  <SingleLogoutService Binding="HTTP-POST" .../>
  <SingleLogoutService Binding="SOAP" .../>

  <!-- Multiple ACS entries by index, used to switch cases -->
  <AssertionConsumerService index="0" Binding="HTTP-POST"     isDefault="true"/>
  <AssertionConsumerService index="1" Binding="HTTP-Artifact"/>   <!-- Phase 2 -->
  <AssertionConsumerService index="2" Binding="PAOS"/>            <!-- ECP -->
  <AssertionConsumerService index="3" Binding="HTTP-POST"/>       <!-- Reserve -->

  <!-- Used to verify IIP-IDP04 -->
  <AttributeConsumingService index="0">
    <RequestedAttribute .../>
  </AttributeConsumingService>
</SPSSODescriptor>
```

#### Metadata as the Test IdP (when testing an SP)

```xml
<IDPSSODescriptor WantAuthnRequestsSigned="false" ...>
  <KeyDescriptor use="signing"> × 2      <!-- Key rollover test -->
  <KeyDescriptor use="encryption"> × 1
  <SingleSignOnService Binding="HTTP-Redirect" .../>
  <SingleSignOnService Binding="HTTP-POST" .../>
  <SingleLogoutService Binding="HTTP-Redirect|HTTP-POST|SOAP" .../>
  <NameIDFormat>persistent</NameIDFormat>
  <NameIDFormat>transient</NameIDFormat>
</IDPSSODescriptor>
```

The Test Plan also retains **keys not included in the metadata** (for Phase 4, to check whether a target rejects signatures made with an unregistered key).

### Mechanism for switching cases

| Direction | Who initiates | How the case is identified |
|---|---|---|
| **IdP testing** | Suite (Test SP) | The Suite creates the AuthnRequest, so it has freedom. Put the case ID in `RelayState` and match it with `InResponseTo`. The ACS index / Binding can also be selected freely. |
| **SP testing — response processing** | Suite (Test IdP, unsolicited) | The Suite generates the Response and POSTs it to the target ACS through the browser. The case ID is retained in the Suite's state. |
| **SP testing — request generation** | Target SP | **Arming method**. After declaring in the UI, “Treat the next AuthnRequest received as case N,” the user starts login at the SP. |

> **Why the arming method is necessary**: The destination of the AuthnRequest issued by the SP is the Location of the `SingleSignOnService` selected by the SP from the Test IdP's
> metadata. The URL cannot be changed for each case.
> Therefore, the Suite has no choice but to retain “which case is currently being tested” in its state.
> Multiple cases cannot be armed at the same time → **SP request-generation tests run sequentially**.
>
> Response-processing tests (such as whether an SP rejects an invalid Assertion), on the other hand, are initiated by the Suite, so **parallelization and automation are possible**.
> Most SP profile tests should be placed in this category.
> However, some SPs disable unsolicited (IdP-initiated) SSO, so the Test Plan has `sp_accepts_unsolicited: yes/no`; when it is no, fall back to the arming method.

### Handling sessions

- In IdP testing, the user must log in to the target IdP. **Log in only once initially, then pass automatically using the IdP-side SSO session.**
- Only the `ForceAuthn` (IIP-IDP06) test requires reauthentication, so place it toward the end of the test order.
- Because the expected result for `IsPassive` (IIP-IDP07) changes depending on whether a session exists, assume immediately beforehand that “the user is logged in.”

## 3.5. Mandatory Requirement for Access to Raw Requests

This is stated explicitly because it is a technical constraint on web framework selection.

**The signature of the HTTP-Redirect binding covers the query string itself before URL decoding.**

```
SAMLRequest=fZJNT%2BMwEIb%2F...&RelayState=abc&SigAlg=http%3A%2F%2F...
└──────────────── this raw byte sequence is the signature input ────────────────┘
```

If parameters are parsed and reconstructed, signature verification breaks due to differences in percent encoding
(`%2F` and `/`, `+` and `%20`, and letter case).
Likewise, for the HTTP-POST binding, the base64 string must not be re-encoded.

Therefore, the Suite must satisfy the following.

- On receipt, it must be able to obtain the **raw query string** (equivalent to `getQueryString()`) and the **raw body bytes**.
- The framework and filters must not normalize or re-encode the URL.
- When a reverse proxy is used, it must be configured so that the proxy does not rewrite the query string (document this in the README).
- The Transcript must retain **both the raw value before decoding and the value after decoding**.

> Misunderstanding this would turn the test suite into one that falsely determines that “the target's signature is invalid.”
> Fix the path that retains raw bytes in place with a test at the earliest stage of implementation.

## 3.7. ★ Role Placement of ECP (Enhanced Client or Proxy)

IIP-IDP13–16 are MUST obligations imposed on the **IdP**, so what is tested in Phase 1 is **the target IdP's ECP support**. In this case, Samlier acts as an **ECP client + SP**, and **not as the Test IdP**.

ECP consists of two segments: “ECP client ↔ SP” and “ECP client ↔ IdP”
([OASIS ECP Profile v2.0](https://docs.oasis-open.org/security/saml/Post2.0/saml-ecp/v2.0/saml-ecp-v2.0.html)).
Because Samlier also acts as the SP, the segment with the SP can be completed internally.

```
┌───────────────────────────────────────────────────────────┐
│ Samlier                                                   │
│                                                           │
│  ┌────────────┐  ① Generate AuthnRequest itself (as SP)   │
│  │ Test SP    │─────────────┐                             │
│  │ (peer/sp)  │             ▼                             │
│  └────────────┘   ┌──────────────────┐                    │
│         ▲         │ ECP Client       │                    │
│         │         │ (peer/ecp)       │                    │
│         │         └────────┬─────────┘                    │
│         │                  │ ② Remove **all** SOAP        │
│         │                  │    headers originating from │
│         │                  │    the SP, then send the    │
│         │                  │    AuthnRequest by SOAP     │
│         │                  │    + HTTP Basic auth        │
│         │                  │    (+ ECP's own cb:ChannelBindings) │
│         │                  ▼                             │
│         │        ┌────────────────────────┐              │
│         │        │  Target IdP            │              │
│         │        │  SOAP SSO endpoint     │              │
│         │        └───────────┬────────────┘              │
│         │                    │ ③ SOAP Response           │
│         │                    │   (ecp:Response, Assertion)│
│         │                    ▼                           │
│  ┌──────┴───────────────────────────────┐                │
│  │ ④ POST /p/{plan}/sp/paos             │  ⑤ Verify/evaluate │
│  │    PAOS Response Consumer            │                │
│  └──────────────────────────────────────┘                │
└───────────────────────────────────────────────────────────┘
```

### ★ The set of headers differs by segment (ECP v2 §2.3.4)

> *Any header blocks received from the service provider **MUST be removed**.*

ECP **removes** the SOAP header blocks received from the SP before forwarding the request to the IdP.
PAOS is primarily for the **ECP ↔ SP** segment; **sending PAOS headers to the IdP is incorrect**.

| Segment | SOAP headers |
|---|---|
| SP → ECP | `paos:Request`, `ecp:Request`, `ecp:RelayState`, `cb:ChannelBindings` (added by the SP) |
| **ECP → IdP** | **Remove all of the above**. Only `cb:ChannelBindings` added by ECP itself (representing the client↔SP channel) |
| IdP → ECP | `ecp:Response`, `cb:ChannelBindings` (matching), `samlec:*` |
| ECP → SP | `paos:Response`, `ecp:RelayState` (return the one received from the SP) |

Because Samlier also acts as the SP, ① completes internally, but the implementation must enforce **not carrying over the headers from ① when constructing ②**
(make `EcpClient` use a data structure that does not retain headers originating from the SP).

Design implications:

- The Test SP metadata must always include **`<AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:PAOS" index="2">`** (required to verify IIP-IDP16, “importing ECP configuration from metadata”).
- Verify that `ecp:Response/@AssertionConsumerServiceURL` **matches the PAOS ACS in the metadata**.
  An IdP that returns a non-matching URL violates IIP-IDP16 and is also important from an Open Redirect perspective.
- Because **the browser is not used at all**, this can be completely automated as `AUTOMATED`.
  This is the area most amenable to automation among the IIP tests.
- **An ECP endpoint on the Test IdP side is unnecessary in Phase 1**.
  Because the IIP imposes no ECP obligation on SPs, testing SP ECP support begins in Phase 2 or later.

### ★ ECP and SAML-EC are separate specifications and require separate cases

IIP-IDP13–16 reference **[SAML2ECP] ECP Profile v2.0**, but **only IIP-IDP15 references a different document**.

> *Identity Providers MUST support the generation and inclusion of a random key
> in accordance with **[SAML-EC], Section 5.3.1**.*

`[SAML-EC]` is the IETF kitten WG's
[SAML Enhanced Client SASL and GSS-API Mechanisms](https://datatracker.ietf.org/doc/html/draft-ietf-kitten-sasl-saml-ec-16)
(an Internet-Draft), not the ECP Profile.

| Obligation | Referenced specification | Test target | Can it be verified in an ordinary ECP round trip? |
|---|---|---|---|
| IIP-IDP13.a | ECP Profile v2.0 | `SubjectConfirmation/@Method` = Bearer, `@Recipient` | ✅ |
| **IIP-IDP13.b** | ECP Profile v2.0 §2.3 | **Verification of channel bindings** | ✅ (the case group below is required) |
| IIP-IDP14 | RFC 2617 | HTTP Basic authentication | ✅ |
| **IIP-IDP15** | **[SAML-EC] §5.3.1** | **`<samlec:GeneratedKey>`** (inside the Assertion's `<saml:Advice>`) | ❌ **A separate case is required** |
| IIP-IDP16 | ECP Profile v2.0 §2.3.10 | Importing configuration from metadata | ✅ |

Therefore, divide `peer/ecp/` into two paths.

```
peer/ecp/
  ├─ profile/   ECP Profile v2.0 client (IDP13, IDP14, IDP16)
  └─ samlec/    SAML-EC extension client (IDP15)
                 Send a request for SAML-EC and inspect samlec:GeneratedKey in Advice
```

**Fix the version of the referenced draft in `specs.yaml`** (rule 28 of [05 §5](05-test-definition-format.md)).
Because the section numbers and element definitions of a draft may change by version,
reproducibility is impossible unless the result records which version contains “§5.3.1.”

### ★ Test case group for channel bindings (IIP-IDP13.b)

The original text says *MUST support "Bearer" subject confirmation **and verification of channel bindings***, and the **verification** of channel bindings is included in the MUST.
ECP v2 §2.3.6.2 also specifies the **output** when they match.

> *…MUST include at least one `<cb:ChannelBindings>` element … as **SOAP header blocks** in its message to the client.*
> *…MUST include at least one `<cb:ChannelBindings>` element in the **`<saml:Advice>`** element of any `<saml:Assertion>` elements that it returns.*
> *The `<samlp:AuthnRequest>` message **MUST be signed** if the channel bindings extension option is used.*

| # | Input | Expected |
|---|---|---|
| 1 | Channel bindings between the SP and ECP client **match**. The `AuthnRequest` is signed. | In addition to successful authentication, **`cb:ChannelBindings` is included in both (a) the SOAP header blocks of the response and (b) the `<saml:Advice>` of the returned Assertion**. ★ If it appears in only one of the two, this violates IIP-IDP13.b. |
| 2 | **Mismatch** | An error `<samlp:Response>` is returned. An Assertion must not be returned. |
| 3 | Channel binding exists only in the `AuthnRequest`'s `<Extensions>`. | Handle it according to ECP v2. At a minimum, **do not return a successful Assertion without verification**. |
| 4 | Exists only on the SOAP header side. | Same as above. |
| 5 | Channel binding is used, but the **`AuthnRequest` is unsigned**. | ★ Make the expectation explicit: because the specification makes signing a MUST, **an error Response is returned**. If an Assertion is issued without a signature, return FAIL. |

Cases 2 and 5 are negative tests, so follow the evidence ladder in [03 §5](03-test-model.md).
Because ECP is a back-channel, automatic determination at L1 (SAML Status error) is highly likely.

## 4. Endpoint Design

```
GET  /                              Web UI
GET  /api/plans                     Test Plan list
POST /api/plans                     Create Test Plan
GET  /api/plans/{id}                Test Plan details (+ issued entityID / metadata URL)
POST /api/plans/{id}/runs           Start Test Run
GET  /api/runs/{id}                 Run status (streamed via SSE)
POST /api/runs/{id}/cases/{cid}/arm     Arm case (SP testing)
POST /api/runs/{id}/cases/{cid}/attest  Declare user's observation result
GET  /api/runs/{id}/transcript      Communication log
GET  /api/runs/{id}/result.json     Result JSON
POST /api/runs/{id}/publish         Publish result (opt-in)

--- SAML protocol surface ---
GET  /p/{plan}/metadata             Test Peer metadata (signed)
GET  /p/{plan}/metadata?variant=X   Abnormal-path metadata (expired / unsigned / badsig / no-validUntil)
GET  /mdq/{encodedEntityID}         Metadata Query Protocol
POST /p/{plan}/sp/acs/{index}       Test SP: Assertion Consumer Service
GET|POST /p/{plan}/sp/slo           Test SP: Single Logout
POST /p/{plan}/sp/slo/soap          Test SP: SOAP SLO
POST /p/{plan}/sp/paos              Test SP: ECP (PAOS) Response Consumer ★
GET|POST /p/{plan}/idp/sso          Test IdP: SSO
GET|POST /p/{plan}/idp/slo          Test IdP: SLO
POST /p/{plan}/idp/slo/soap         Test IdP: SOAP SLO
GET  /p/{plan}/start/{caseId}       Starting point for browser operation (user clicks)
```

In Hosted mode, `POST /api/plans` creates the Plan, initial Run, and hashed access grant atomically and returns its
one-time management URL. The same transaction rejects creation while any non-terminal Run exists for that target
entity ID, including when requests race.
While a Plan has a non-terminal Run, Hosted mode also rejects changing that Plan's target entity ID. Plan updates
and Run provisioning share the same immediate SQLite write lock, so a concurrent update cannot move an active Run
onto an already occupied target after the provisioning check.
Every other Plan endpoint, Run details, preflight, and Run events require the HttpOnly management session;
mutations additionally require the matching CSRF token. `GET /api/plans` returns only the Plan associated
with the caller's current Run session. Plan responses never include `test_user_hint` or the target metadata
source location.

> Replacing metadata with `?variant=` is essential for verifying IIP-MD03 / MD04.
> However, **switching the variant does not necessarily mean that the target immediately refreshes its cache**, so
> manage the variant in the Suite's state as “the current distribution state of the Test Plan,” and insert an interactive step instructing the user to “reload the target's metadata.”

## 5. Transcript Recorder

Retain all grounds for the determination. This accounts for half of the Suite's value.

### 5.1 What to record

```
- Direction (inbound / outbound), timestamp (ms), correlation ID
- HTTP: method, URL, status, headers (after removal in §5.2), raw body (after removal in §5.2)
- SAML:
    - Raw byte sequence before encoding (the deflate+base64-decoded Redirect payload, and the base64-decoded POST payload)
    - Formatted XML
    - Summary of the result parsed by OpenSAML (Issuer, ID, InResponseTo, Destination, Conditions, Status ...)
    - Signature verification details (reference URI, list of Transforms, key used, verification result)
- The expressions used for the determination and their evaluation results
```

In the UI, it must be possible to navigate from test result → reason for determination → applicable transaction → raw XML with one click.

### 5.2 ★ Remove confidential information before submitting it to the Recorder

**Scrubbing at publication time is too late.** ECP testing (IIP-IDP14) uses HTTP Basic authentication, so if the `Authorization` header is recorded as-is, the credentials remain **persisted in `/data` as reversible Base64**. Even without publication, they could leak from the disk, backups, or Transcript downloads.

The Recorder has a **Redactor** at its entry point and irreversibly removes data before persistence.

| Target | Processing |
|---|---|
| `Authorization` / `Proxy-Authorization` | Discard the value and replace it with `Authorization: <redacted: Basic, 42 bytes>` |
| `Cookie` / `Set-Cookie` | Retain the name and replace the value with `<redacted: 24 bytes>` |
| Body of `application/x-www-form-urlencoded` | Remove values whose key name matches `password` / `passwd` / `pwd` / `secret` / `token` / `otp` / `pin` |
| `test_user_hint` of the Test Plan | Do not include it in the Transcript |
| ECP credentials | **Retain in memory only during execution**. Do not write them to `CaseState` either ([05 §4.2](05-test-definition-format.md)) |
| SAML `<saml:AttributeValue>` | **Retain in the Transcript** (required for the determination). Mask it at publication time ([06 §4](06-results-and-publication.md)) |

Key design points:

- The Redactor is **inside `Transcript.record()`**; do not create an API that can bypass it.
- Removal is **irreversible**. Do not store data in a form that can be decrypted later.
- Retain the fact that removal occurred (header name and byte length). It must be possible to tell during debugging whether the data existed.
- The design in which case implementations cannot issue HTTP except through `ctx.fetch()` ([05 §4.3](05-test-definition-format.md)) establishes this guarantee.
- With `RedactorTest`, after executing an ECP round trip with Basic authentication, verify that **the credentials do not appear in any byte sequence under `/data`**.

## 6. Code Structure (Proposal)

```
samlier/
├── core/            Domain models (Plan, Run, Case, Result, Verdict)
├── saml/
│   ├── normal/      OpenSAML-based generation and parsing
│   ├── raw/         DOM/StAX-based generation (foundation for Phase 4)
│   ├── crypto/      Santuario wrapper, key generation, algorithm definitions
│   └── metadata/    Metadata generation, variants, MDQ
├── peer/
│   ├── sp/          Test SP endpoints and state (including PAOS ACS)
│   ├── idp/         Test IdP endpoints and state
│   └── ecp/         ECP client (tests the target IdP's ECP support. §3.7)
├── runner/          State machine, arming, attestation
├── tests/
│   ├── defs/        *.yaml (test definitions, embedded as resources)
│   └── impl/        Java implementations. Bound 1:1 to YAML ids
├── store/           SQLite access
├── api/             REST + SSE (Javalin)
├── auth/            Administrative access for the hosted version (secret URL, future OIDC RP)
│                    ★ Completely separate sessions, cookies, and origin from peer/
└── web/             React + Vite (TypeScript). report.html is also a static build from the same application
```

In CI, verify that “there is an implementation class corresponding to each YAML” and “there is a YAML corresponding to each implementation class.”
