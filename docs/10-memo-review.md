# 10. Review of the Original Concept Memo

Subject: “Summary of the SAML Conformance Test Suite Concept” (presented 2026-08-25)

**Overall assessment**: The direction, scope boundaries, and technical-selection foundation are all sound.
The decision to limit Phase 1 to the Kantara Implementation Profile is particularly good.
However, the **practical constraints of externally testing SAML as a black box** have not been incorporated,
and there are 8 points where proceeding directly to implementation would require redesign midway through Phase 1.

---

## A. Logical Breakdowns and Contradictions (Corrections Required)

### A-1. ★ The premise that `NOT SUPPORTED` and `FAIL` can be distinguished does not hold

> Memo: “Optional functionality that has not been implemented should be represented as a result such as NOT SUPPORTED, distinguished from FAIL.”

Of the <!--g1:requirements-->69<!--/g1--> requirements in Kantara IIP v1.1, **approximately 6 are SHOULD / MAY / OPTIONAL**,
while all the rest are MUST. Declaring that a MUST requirement is “not implemented” means, by definition, that the implementation does not conform to the profile, and it must not be distinguished from FAIL.

→ Correction: Decide mechanically by RFC2119 level.
Unimplemented MUST = `FAIL(declared-unsupported)` / unimplemented SHOULD = `WARNING` / unimplemented MAY = `NOT_SUPPORTED`.
Limit `NOT_APPLICABLE` to cases determined by the Profile and Test Plan configuration, rather than by a user's declaration.
→ [03-test-model.md §4](03-test-model.md)

### A-2. The numbers “PASS 74 / FAIL 0 / N/A 8” on the results screen do not match the number of requirements

The requirements evaluated by the IIP v1.1 IdP profile are **Common 31 + IdP 21 = 52**.
The number 74 can only arise if it refers to the number of “test cases” (1 requirement : N cases).

→ Correction: The report must explicitly show **both requirement granularity and case granularity**.
“Requirements 52 / Test cases 74.” Mixing them will mislead readers.
→ [06-results-and-publication.md §1](06-results-and-publication.md)

### A-3. ★ `docker run -p 8080:8080` → `http://localhost:8080` alone cannot complete a run

> Memo: “Ideally, the user should only need to run docker run -p 8080:8080 ... and open http://localhost:8080.”

This works only for tests in the front channel (via a browser).
Among the IIP requirements, **metadata-related requirements (MD01〜MD12) and SOAP SLO / ECP
cannot be executed unless the target server can reach the Suite directly**.
This accounts for approximately 40% of all requirements.

→ Correction: Make `PUBLIC_BASE_URL` a mandatory concept, determine reachability during Preflight,
and explicitly show the operating mode as “Local-only / Reachable / Hosted.”
When creating a Test Plan, warn in advance: “N requirements will not be evaluated in this configuration.”
→ [07-deployment-and-networking.md §2](07-deployment-and-networking.md)

### A-4. ★ The trust model for shared URLs conflicts with the objective

> Memo: “After a Test Run ends, a URL such as `https://samltest.example/results/01K3...` can be issued.”
> Memo: “The reproducible test result itself should serve as proof of quality.”

Because the design allows anyone to run the Suite when self-hosted, **the result JSON can be freely fabricated**.
If a public URL can be issued for a fabricated result, the premise that “the result itself is proof of quality” collapses.

→ Correction: Divide trust levels according to the execution environment into three levels, and always display the level on the public page.
For Phase 1, recommend “self-hosted = local export only / Hosted execution = shared URL available.”
→ [06-results-and-publication.md §3](06-results-and-publication.md), Decision [D-04](09-open-decisions.md)

### A-5. The arrow order in the diagram for testing an SP is reversed

> Memo:
> ```
> Test IdP → Response / Assertion → Target SP → AuthnRequest etc. → Test Suite
> ```

In SP-initiated SSO, **the Target SP sends the AuthnRequest first**, and the Test IdP returns the Response.
For IdP-initiated (unsolicited) SSO, the Response comes first and an AuthnRequest does not exist in the first place.
The two flows have been mixed together.

→ Correction: Put the two flows in separate diagrams. This distinction is also important for implementation (the next item).

### A-6. ★ “The Suite plays the opposite side” alone cannot distinguish SP request-generation tests

The OIDF Conformance Suite can issue a new issuer / client for each test,
but **SAML has no dynamic client registration**. Registration with the target must be done manually.
If the entityID is changed for each test case, the user will be forced to perform dozens of registration operations.

Furthermore, the destination of the AuthnRequest issued by the SP is one URL selected by the SP from the Test IdP metadata,
so **cases cannot be identified by URL**.

→ Correction:
- **1 Test Plan = 1 entityID = 1 “all-in-one metadata” document** (include multiple keys, ACS indexes, and bindings from the outset)
- Switch cases as follows: for IdP tests, use `RelayState` / `InResponseTo` / ACS index;
  for response-processing tests in SP tests, use Suite-initiated unsolicited responses;
  for request-generation tests in SP tests, use an **arming mechanism** (declare in the UI, “Treat the next AuthnRequest as case N”)
→ [02-architecture.md §3](02-architecture.md)

### A-7. The `expected` field in the test-definition YAML appears to be declaratively expressible

> Memo:
> ```yaml
> expected:
>   expired_metadata: reject
> ```

It is not possible to define in YAML what constitutes a determination that “reject” has occurred.
If development proceeds as-is, the YAML will contain part of the implementation in an incomplete form, and the meaning will not be clear without examining both.

→ Correction: **YAML is normative about “what, why, and which specification supports it,” while Java is normative about “how it is determined,”**
and the roles must be separated. Make YAML's `expected` human-oriented prose, and put the determination logic in implementation classes.
Enforce the 1:1 correspondence between the two in CI.
→ [05-test-definition-format.md](05-test-definition-format.md)

### A-8. The criteria for distinguishing `IdP Basic / IdP Full` are undefined

The original IIP does not distinguish Basic / Full. Unless the basis for the division is decided,
the meaning of “passed Basic” cannot be explained.

→ Correction: Establish the following criteria: **Core = among MUST requirements, SSO / Metadata / Algorithms; Full = Core + SLO + ECP + SHOULD and below**.
**State explicitly in the report that this classification is specific to the Suite.**
→ [01-scope-and-roadmap.md](01-scope-and-roadmap.md)、[04-requirement-coverage.md](04-requirement-coverage.md)

---

## B. Critical Omissions (Items Added)

| # | Omitted issue | Why it is critical | Added to |
|---|---|---|---|
| B-1 ★ | **Observability of determinations**. From outside, it is often impossible to mechanically observe that “the target rejected it” | Without this, most negative tests become “nothing happened → PASS,” making the report meaningless | [03 §3, §5](03-test-model.md) |
| B-2 ★ | **Approximately 40% of tests require configuration changes on the target side** (metadata reload, attribute-release settings, etc.) | “Register the metadata URL once and run everything” does not work. The UI needs a `WAITING_CONFIG` step | [03 §8](03-test-model.md), [04](04-requirement-coverage.md) |
| B-3 | **The browser is part of the test path**: user login operations, reuse of the SSO session, and ordering dependencies involving `ForceAuthn` / SLO | If implemented without considering ordering, tests will damage one another's sessions and produce unstable results | [02 §3](02-architecture.md), [03 §9](03-test-model.md) |
| B-4 ★ | **The Suite's own security**. Because it is a tool that connects to arbitrary URLs, the Hosted version can become a launch point for SSRF. Because it parses XML received from targets, it can also become a target for XXE | This cannot be added after the fact if the Suite is released as a public service | [08](08-suite-security.md) |
| B-5 | **HTTPS / SameSite Cookie constraints**. Many implementations reject an `http://` ACS, and the HTTP-POST binding requires `SameSite=None; Secure` | HTTP on localhost is not practical | [07 §3](07-deployment-and-networking.md) |
| B-6 | **Time synchronization**. Clock drift in a container can break every test | It exhausts users through failures with no apparent cause | [07 §4](07-deployment-and-networking.md) |
| B-7 | **Record the Test Plan configuration declaration in the result** (declarations of implemented functionality, interpretation values for clock skew, etc.) | Without this, two results cannot be compared, and “PASS 74” loses its meaning | [03 §2](03-test-model.md), [06 §1](06-results-and-publication.md) |
| B-8 | **Personal information leaking into public results**. When testing with a real IdP, the Assertion contains the name and email address of a real user | The most likely incident. Masking by default is mandatory | [06 §4](06-results-and-publication.md) |
| B-9 | **Relationship to existing OSS**. codice/saml-conformance (LGPL-3.0, Kotlin, IdP-only, targeting OASIS Core) | The code cannot be reused for licensing reasons. Differentiation must also be explained | [00 §4](00-concept.md) |
| B-10 | **Scope of quotations from the original specification** (IPR in Kantara documents) | Reproducing the full requirement text could create an intellectual-property issue | [09 D-11](09-open-decisions.md) |
| B-11 | **Preflight checks** | Without them, users will encounter failures with no apparent cause | [03 §10](03-test-model.md) |
| B-12 | **Reproducibility of results**. A digest of the test definitions is needed in addition to the Suite version | Results can change if the definitions change, even with the same version | [06 §1](06-results-and-publication.md) |
| B-13 | **ECP profile** (IIP-IDP13〜16). MUST in the IIP | It is not mentioned in the memo. Moreover, **ECP is one of the few areas that can be fully automated using only the back channel**, giving it high implementation value | [04](04-requirement-coverage.md) |

---

## C. Fact-Checking and Corrections

| Item | Memo's wording | Verification result |
|---|---|---|
| Kantara IIP version | Not specified | **v1.1 (2019-12-18)** is the latest Kantara Recommendation. It was explicitly designated as the Phase 1 target |
| Requirement IDs | `IIP-G01` `IIP-G03` `IIP-MD03` `IIP-MD04` `IIP-MD07` `IIP-SSO01` `IIP-SSO02` `IIP-IDP08` | **All exist, and the memo's explanations are correct** (Clock skew / DTD rejection / Metadata signature / Metadata expiration / Multiple signing keys / Browser SSO / Redirect-POST / RequestedAuthnContext). Faithful to the original text |
| Total number of requirements | Not stated | Common 31 / SP 17 / IdP 21 = 69 |
| “SAML2Int” in Phase 3 | “SAML2Int / Deployment Profile” | saml2int.org (v0.2.1) is a historical document. The current successor is **Kantara SAML V2.0 Deployment Profile for Federation Interoperability v2.0**. The reference was replaced |
| Java 21 + OpenSAML | First candidate | **OpenSAML 5.x requires Java 17+, and is Apache-2.0. No issue with Java 21. Appropriate** |
| Apache Santuario | Use for XML Security | Appropriate. In addition, the design now notes that **creating an invalid signature requires bypassing OpenSAML's Signer and invoking Santuario directly** |
| “Do not depend only on OpenSAML” | Retain low-level XML operations for Phase 4 | **The correct decision**. However, adding this later in Phase 4 would duplicate and break the generation paths, so the design now notes that **only the interface should be separated in Phase 1** |
| Docker-only deployment | Do not create a Cloudflare Workers version | **Correct**. The XML Security implementation assumes the JVM, and reimplementing it on Workers is not realistic |
| SQLite | First candidate | Appropriate for Phase 1. Large data such as Transcripts should be stored in files rather than the database, and the design now notes that the database access layer should remain thin to preserve the option of replacing it with PostgreSQL |

---

## D. Good Decisions to Keep Unchanged

- **Limit Phase 1 to the Kantara IIP**. Requirement IDs are assigned, making correspondence with tests easy to establish
- **Do not call it a certification authority; retain “Tested.”** This was documented explicitly as a terminology convention ([06 §3](06-results-and-publication.md))
- **Make it an independent OSS project without Authrim-specific code**. From the Suite's perspective, Authrim and Keycloak are equally external implementations
- **Use the same image for the Hosted and self-hosted versions**. Do not create branches in determination logic
- **Separate the meaning of tests from the code** (the method was corrected, but the intention is sound)
- **Docker-only deployment**
- **Keep the Phase 4 Security Profile in view from the beginning** (but do not build it)

---

## E. Additional Design Proposals

| # | Proposal | Effect |
|---|---|---|
| E-1 | **“All-in-one metadata”: 1 Test Plan = 1 entityID** | Target registration is completed once. The largest practical factor affecting whether the Suite can be adopted |
| E-2 | **Arming mechanism** (SP request-generation tests) | The only realistic way to work around SAML's lack of dynamic registration |
| E-3 | **Evidence ladder L1–L4 + treat `INDETERMINATE` as a failed result** | Structurally prevents “nothing happened → PASS.” The core of the Suite's credibility |
| E-4 | **Run IIP-IDP05 (return an error Response) first as the highest priority** | An IdP that satisfies this requirement can automatically determine other negative tests. An execution order that maximizes detection power |
| E-5 | **Quick-run mode** (skip tests requiring configuration changes on the target side) | Keep the first-run experience to 10 minutes. Lower the adoption barrier |
| E-6 | **Preflight checks** | Eliminate failures with no apparent cause |
| E-7 | **Make the coverage table machine-readable and generate Markdown** | Handwritten tables inevitably diverge from reality |
| E-8 | **Add “results differ across 3 implementations” to the Phase 1 success criteria** | An all-PASS report does not demonstrate detection power. An acceptance criterion proving that the Suite is “effective” |
| E-9 | **Make the numerical interpretation of “reasonable” a Test Plan parameter and record it in the result** | Avoid arbitrary Suite decisions for requirements with no numerical value in the specification (such as IIP-G01) |
| E-10 | **Consider separating the UI and Test Peer endpoints into different origins** | Prevent target-derived content from reaching the Suite's UI session |
