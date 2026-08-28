# 00. Concept

## 1. Problem

OIDC / OAuth has the OpenID Foundation Conformance Suite and OpenID Certification,
which make it possible to demonstrate reproducibly that “this product conforms to this profile.”

SAML has no widely recognized mechanism equivalent to this.
At the same time, the materials for assessing implementation quality are publicly available.

| Document | Issuer | Positioning |
|---|---|---|
| SAML V2.0 Core / Bindings / Profiles / Metadata | OASIS | Core specifications |
| SAML V2.0 Conformance Requirements | OASIS | Definition of conformance classes (not executable tests) |
| Security and Privacy Considerations for SAML V2.0 | OASIS | Basis for the attacker model |
| **SAML V2.0 Implementation Profile for Federation Interoperability v1.1** | Kantara Initiative (2019-12-18) | **Interoperability requirements for implementers. Scope of Phase 1** |
| SAML V2.0 Deployment Profile for Federation Interoperability v2.0 | Kantara Initiative | For deployers (successor to the former SAML2int) |
| Metadata Interoperability Profile | OASIS | Handling of metadata keys |

In short, **“what must be satisfied” is documented, but there is no “common means of verifying whether it is satisfied.”**

## 2. What we are building

> An OSS tool that translates specification requirements into executable tests so that anyone can verify a SAML implementation under the same conditions.

It will not call itself a certification body. **Reproducible test results themselves** will serve as the evidence of quality and be shareable.

### Why start with the Kantara Implementation Profile

- It is written for software implementers and does not depend on deployment-specific circumstances.
- All <!--g1:requirements-->69<!--/g1--> requirements have unique `[IIP-xxNN]` identifiers (making 1:1 / 1:N mappings to tests possible).
- Nearly all are MUST requirements, making them suitable for binary “is it observed?” determinations.
- Implementations such as Keycloak / Shibboleth / SimpleSAMLphp / Authentik actually refer to it.

## 3. Non-goals

| What we will not do | Reason |
|---|---|
| Issue certification or authentication (Certification) | We have no legitimacy as a certification body. We stop at “Tested.” |
| Use terms such as “Certified” or “Compliant” in results | They could mislead. The terms used are `Tested` / `Conformance Test Result`. |
| Support SAML 1.x | Out of scope |
| Provide code dedicated to a specific product (including Authrim) | From the Suite’s perspective, every implementation is equally an external implementation. |
| Provide the SAML library / IdP / SP product itself | A Test Peer is “the other party for testing” and is not intended for production use. |
| Performance or load testing | Out of scope |
| Intrusive operations against the target system | Even in the Phase 4 Security Profile, only the normal protocol path with the target will be used. |

## 4. Relationship to existing tools

Existing OSS / services investigated:

| Name | What it does | Difference from this Suite |
|---|---|---|
| [codice/saml-conformance](https://github.com/codice/saml-conformance) | Black-box testing of an IdP against the SAML Core specification. Kotlin/Java, CLI-based | **IdP-only**; SP testing is unavailable. It targets OASIS Core, not the Kantara IIP. Because it is **LGPL-3.0**, code reuse is subject to substantial license constraints. No Web UI or result sharing. Maintenance has effectively stalled. |
| SAMLtest.id (Shibboleth) | Public test IdP / SP. Connect manually to check operation | It shows whether something works, but does not produce pass/fail reports by requirement. |
| samltool.com / SAMLTracer, etc. | Utilities for decoding and validating SAML messages | One-off analysis tools, with no concept of a test plan or report. |
| [spid-sp-test](https://pypi.org/project/spid-sp-test/) / AgID spid-saml-check | Conformance checkers dedicated to the Italian SPID profile | **Dedicated to a national profile.** Its structure is highly informative (report JSON, CLI), but it cannot be used for generic SAML. |
| SAML Raider / WS-Attacker / EsPReSSO | Attack testing against SAML (such as Burp extensions) | For manual penetration testing. A reference for Phase 4. Does not produce conformance reports. |

**This Suite’s distinctiveness** comes down to these four points.

1. Test **both directions**: IdP and SP.
2. Produce **Requirement ID-level** reports (with traceability to the specification basis).
3. With a Web UI + Docker, allow users without specialized knowledge to create and run a **Test Plan**.
4. Distribute results in a **shareable format**.

> Note: codice/saml-conformance is LGPL-3.0, while **Samlier is Apache-2.0**.
> Design and test perspectives may be consulted freely, but **code must not be copied**.
> `ctk/idp/NotTested.md` (a list of requirements that cannot be verified externally) is useful as a conceptual reference.

## 5. Phase 1 success criteria

> Any SAML IdP/SP implementer can start Docker, use the Web UI to run tests based on the Kantara Implementation Profile v1.1,
> inspect the PASS/FAIL and basis for each Requirement, and, if desired, present the result to a third party as a
> public URL.

Add verifiable acceptance criteria to this.

### ★ Prove detection power with mutant peers

**Do not make “the results differ for three reference implementations” a completion condition.**
No difference does not indicate a Suite defect — all three products may conform, or any difference may merely be a configuration difference. Real products are **not oracles**.

Instead, provide **mutant Test IdP / Test SP** implementations with known violations injected,
and make “the targeted obligation is always detected as violated” a golden test.

#### ★ Terminology: a mutant is the target (SUT), not the Suite’s Test Peer

Samlier’s `peer/` (Test IdP / Test SP) is **the inspecting side** and always operates correctly.
A mutant is injected into **the inspected side (SUT: System Under Test)**.
`tests/mutants/` defines “target implementations that intentionally violate requirements” and is separate from `peer/`.
Confusing them leads to the incorrect implementation of “break the Suite side to measure detection power.”

#### ★ The oracle is a difference from baseline, not an absolute value

“All obligations are PASS for a normal SUT” **does not hold**.
Because of role differences (`IIP-IDP*` in the SP profile), conditional obligations, `CONFIG`, and `ATTESTED`, no single Run makes all obligations PASS.
Likewise, “no obligation may PASS under `reject-everything`” is **incorrect**:
`MUST_NOT` obligations requiring rejection can be satisfied by uniformly rejecting.

Therefore, first obtain a **baseline outcome vector**, and judge mutants by their **differences** from it.

#### ★ One baseline is insufficient — use a matrix

With an `role: sp` baseline, all **`IIP-IDP*` become `NOT_APPLICABLE`**, so IdP mutants cannot be detected. Differences in Core/Full, conditional features, and `CONFIG` settings are also not covered.

```yaml
# tests/mutants/baselines.yaml
baselines:
  - id: sp-full-slo-enc
    role: sp                     # Role of the SUT (the Suite plays the Test IdP)
    profile: sp-full
    declared_features: { single_logout: true, assertion_encryption: true, ecp: false }
    config_fixture: tests/fixtures/sut/sp-full-slo-enc/    # ★ Results change with configuration differences
    interaction: { allow_browser_steps: true, allow_attestation: true }
  - id: sp-core-minimal
    role: sp
    profile: sp-core
    declared_features: { single_logout: false, assertion_encryption: false }
    config_fixture: tests/fixtures/sut/sp-core-minimal/
  - id: idp-full
    role: idp
    profile: idp-full
    declared_features: { ecp: true, assertion_encryption: true }
    config_fixture: tests/fixtures/sut/idp-full/
  - id: idp-core-no-ecp
    role: idp
    profile: idp-core
    declared_features: { ecp: false }
    config_fixture: tests/fixtures/sut/idp-core-no-ecp/
outcomes:                        # Expected outcome per baseline (all <!--g1:case_target-->543<!--/g1--> obligations)
  sp-full-slo-enc:
    IIP-SP13.a: satisfied
    IIP-SP13.b: satisfied
    IIP-IDP01.a: not_applicable  # Different role
    IIP-SP14.c: not_supported    # Declaration of an unimplemented OPTIONAL
    ...
```

**Write expected values as `outcome`, not Verdict.**
`Evaluator` converts `satisfied` / `violated` to `PASS` / `WARNING` / `NOT_SUPPORTED` by looking at `level` ([docs/05 §2.3](05-test-definition-format.md)).
Writing `FAIL` in a mutant definition would repeat the error of uniformly turning SHOULD obligations into FAIL.

```yaml
# tests/mutants/no-signature-validation.yaml
id: no-signature-validation
base: sp-full-slo-enc            # ★ Explicitly identify the baseline for this mutant
injected_violation_ja: Do not validate XML signatures on Responses at all
expected_changes:                # Obligations that should change from baseline (written as outcome)
  IIP-SP13.a: violated
  IIP-MD07.b: violated
unchanged_required: all_others   # Everything else must match the baseline
```

The key is `unchanged_required: all_others`; without it, **a Suite that marks everything violated could pass the golden test**.

### An unsuccessful control is not a target violation

If the positive control (a conforming implementation passes) fails,
that is **a problem on the Suite side, not a normative violation by the target**.
Do not treat it as `violated` (→ FAIL); treat it as **`control_failed`** and make the case `NOT_VERIFIED(control_failed)`.
Confusing these would display a Suite defect as target nonconformance.

### Initial mutant set

Map these to `detected_by_mutants` in `tests/cases.yaml` during G2.

| mutant | base | Injected violation | `expected_changes` |
|---|---|---|---|
| `no-signature-validation` | sp-full | Do not validate signatures | IIP-SP13.a / IIP-MD07.b |
| `first-key-only` | sp-full | Try only the first of multiple keys | IIP-MD07.b / IIP-SP08.c |
| `first-key-only-idp` | idp-full | Same (EncryptedID in SLO) | IIP-IDP19.c |
| `gcm128-only` | sp-full | Accept only AES128-GCM | IIP-ALG04.b |
| `oaep-sha1-reject` | sp-full | Reject DigestMethod sha1 | IIP-ALG06.c |
| `crash-on-extension` | sp-full / idp-full | Crash on an unknown extension element | IIP-EXT01.b |
| `crash-on-unknown-attribute` | sp-full / idp-full | Crash on an unknown attribute | IIP-EXT01.c |
| `truncate-256` | sp-full / idp-full | Truncate a 256-character value | IIP-G02.a |
| `ignore-force-authn` | idp-full | Ignore `ForceAuthn` | IIP-IDP06.a |
| `no-error-response` | idp-full | Return no Response on error | IIP-IDP05.a / IIP-SSO03.b |
| `single-acs-only` | idp-full | Support only one ACS | IIP-IDP12.a |
| `reject-everything` | Every baseline | Reject everything | **Every case with a positive control changes**; `MUST_NOT` obligations remain at baseline |
| `accept-everything` | Every baseline | Accept everything | **Every case with a negative control changes** |

`reject-everything` / `accept-everything` are **control mutants that validate the controls themselves**.

**Acceptance criteria**

- [ ] The baseline matrix covers **IdP / SP × Core / Full × major conditional features**.
- [ ] Each baseline’s outcome vector is fixed and matches across two executions (reproducibility).
- [ ] Each mutant explicitly specifies `base`.
- [ ] For each mutant, the obligations in `expected_changes` **change as specified**.
- [ ] For each mutant, **all other obligations match the baseline**.
- [ ] With `reject-everything`, **every case with a positive control** changes.
- [ ] With `accept-everything`, **every case with a negative control** changes.
- [ ] **Every obligation is detected by at least one mutant or has `mutant_waiver`**
      ([G2 pass criteria](01-scope-and-roadmap.md)).
- [ ] Control failures are treated as `control_failed` and do not become target FAIL results.
- [ ] From Test Plan creation through result display, the user-facing documentation fits in one `README`.
- [ ] Required target-side configuration fits within **one metadata URL registration + optional settings**.
- [ ] Two executions with the same Suite version and Test Plan produce identical results.
- [ ] Obligations that cannot be verified externally are not mixed into `PASS` ([03](03-test-model.md) verdict vocabulary).

The **position of the reference implementations (Keycloak / Shibboleth / SimpleSAMLphp)** is
“regression detection and interoperability confirmation,” and **not proof of detection power** ([09 D-12](09-open-decisions.md)).

## 6. Relationship with Authrim (explicitly stated)

Authrim originated this project, but Samlier’s code will contain **no Authrim-specific dependencies**.
From the Suite’s perspective, Authrim and Keycloak are equally “external SAML implementations.”

Authrim may appear in three contexts, each treated as a separate matter.

| Context | Treatment |
|---|---|
| **Authrim as a test target** | Exactly the same as any other implementation. No preferential or special treatment. If included in public samples of reference implementations, list it alongside Keycloak / Shibboleth / SimpleSAMLphp using the same format and Test Plan structure. |
| **Authrim as the login IdP for the Hosted version** (future) | Merely a deployment choice. Samlier implements a standards-compliant OIDC RP that can be directed to Keycloak or Auth0 through configuration. → [09 D-09](09-open-decisions.md) |
| **Authrim as a peer for development-time operational checks** | It may be used freely, but do not write tests that pass only with Authrim. CI reference implementations must always include implementations other than Authrim. |

> It is important that this not appear to be a conflict of interest. State the above in the README,
> and treat published Authrim results the same as results for other implementations.
