# 05. Test Definition Format

## 1. Policy

The intent of the original memo is correct. However, its wording can be misunderstood, so it is clarified here.

> **YAML is normative about “what to test, which specification supports it, and why it is tested.”
> Java is normative about “how to test it.”**
>
> Furthermore, **only the requirements catalog (`coverage.yaml`) carries RFC2119 levels**.
> Do not write them in test definitions or implementations.

Do not try to put expected-result logic in YAML.
Example from the original memo:

```yaml
expected:
  expired_metadata: reject     # ← This goes too far into implementation / lacks expressive power
```

Because YAML cannot define what “reject” means, this ultimately depends on interpretation on the Java side.
YAML should contain only **descriptions whose meaning is clear to humans** and **classifications and dependencies used by machines**;
leave determination logic to implementation classes. CI enforces their correspondence.

## 2. Two-Layer Structure: Requirements Catalog and Test Definitions

Put determination levels in the **requirements catalog** and test procedures in the **test definitions**.
Writing `level: MUST` in a test definition can conflict with another case for the same requirement ([03 §1](03-test-model.md)).

```
tests/coverage.yaml     Requirements → obligations (role, condition, RFC2119 level)  ← sole source of determination levels
tests/defs/*.yaml       Test cases → which obligations they verify                 ← procedures and expectations
tests/specs.yaml        Specification catalog (document name, version, URL)
```

### 2.1 Requirements Catalog `tests/coverage.yaml`

```yaml
spec: kantara-fedinterop-impl
version: "1.1"

requirements:
  - id: IIP-SP13
    section: "3.1.1"
    anchor: IIP-SP13
    obligations:
      - key: IIP-SP13.a
        roles: [sp]
        level: MUST
        condition: null
        summary_en: "Support the ability to reject unsigned <samlp:Response> elements"
        summary_ja: "Ability to reject unsigned <samlp:Response> elements"
        testability: BROWSER
        level_assignment: { sp: core }
      - key: IIP-SP13.b
        roles: [sp]
        level: SHOULD                      # ★ Rejecting by default is SHOULD
        condition: null
        summary_en: "Reject unsigned <samlp:Response> elements by default"
        summary_ja: "Reject unsigned Response by default"
        testability: BROWSER
        level_assignment: { sp: core }

  - id: IIP-MD01
    obligations:
      - key: IIP-MD01.a
        roles: [idp]
        level: MUST                        # ★ The level differs by role
        summary_en: "Support metadata acquisition via the Metadata Query Protocol"
        testability: CONFIG
      - key: IIP-MD01.b
        roles: [sp]
        level: SHOULD
        summary_en: "Support metadata acquisition via the Metadata Query Protocol"
        testability: CONFIG
      - key: IIP-MD01.c                    # ★ Conditional MUST hidden in the latter half of the sentence
        roles: [idp, sp]
        level: MUST
        condition:
          predicate: claims_mdq_support
          predicate_kind: CLAIM_BASED      # The source says "claim support for this protocol"
          declared: declared_features.mdq
          observed: []
        summary_en: >
          Implementations that claim support for MDQ must be able to request and
          utilize metadata from one or more MDQ responders for any peer from which
          they receive a SAML message
        testability: CONFIG

  - id: IIP-SP14
    obligations:
      - key: IIP-SP14.a
        roles: [sp]
        level: SHOULD
        summary_en: "Support the SAML V2.0 SingleLogout profile"
      - key: IIP-SP14.b
        roles: [sp]
        level: MUST
        condition: "declared_features.single_logout == true"   # ★ Conditional MUST
        summary_en: "If claiming SLO support, be capable of issuing logout requests"
      - key: IIP-SP14.c
        roles: [sp]
        level: OPTIONAL
        summary_en: "Consumption of logout requests is optional"

  - id: IIP-SP12
    obligations:
      - key: IIP-SP12.a
        roles: [sp]
        level: MUST_NOT
        summary_en: "Do not overload persistent NameIDs with additional semantics"
        testability: NOT_OBSERVABLE        # ★ Correctly has no case
        not_observable_reason_en: >
          The internal semantics a deployment attaches to a persistent NameID are not
          exposed through the SAML protocol surface. No external black-box test can
          distinguish a compliant from a non-compliant generator.
```

#### Evaluate `condition` by Predicate Type

```yaml
      # CAPABILITY_BASED — Actual capability is the condition. Evidence is required.
      - key: IIP-SP15.a
        roles: [sp]
        level: MUST
        condition:
          predicate: supports_single_logout
          predicate_kind: CAPABILITY_BASED
          declared: declared_features.single_logout
          observed:                                # ★ Required for CAPABILITY_BASED
            - target_metadata_has: "md:SPSSODescriptor/md:SingleLogoutService"
            - observed_message: LogoutRequest

      # CLAIM_BASED — Whether support is claimed is itself the condition; observed is unnecessary.
      - key: IIP-SP14.b
        roles: [sp]
        level: MUST
        condition:
          predicate: claims_single_logout
          predicate_kind: CLAIM_BASED
          declared: declared_features.single_logout
          observed: []                             # ★ May be empty (the declaration is the truth value itself)

      # CLASSIFICATION_BASED — Product classification is the condition. Only an explicit exclusion declaration can produce FALSE.
      - key: IIP-IDP13.a
        roles: [idp]
        level: MUST
        condition:
          predicate: not_token_translation_proxy
          predicate_kind: CLASSIFICATION_BASED
          declared: target.kind
          observed: []                             # ★ No observation is available
          declaration_only_exclusion:              # ★ Without this block, FALSE cannot be obtained
            allowed: true
            requires_reason: true                  # Require the user to provide a reason
            statement_en: >
              The target was declared to be a token translation Proxy,
              to which IIP-IDP13 does not apply. This was not verified.
```

Evaluation rules (as established by [03 §1](03-test-model.md)):

| `predicate_kind` | `observed` | When `declared = false` without evidence |
|---|---|---|
| `CLAIM_BASED` | May be empty | Use `FALSE` (the declaration is the truth value itself) |
| `CAPABILITY_BASED` | **Required** | `UNKNOWN` → `NOT_VERIFIED(applicability_undetermined)` |
| `CLASSIFICATION_BASED` | May be empty | `UNKNOWN` by default. `FALSE` only when `declaration_only_exclusion.allowed: true` and there is an **explicit exclusion declaration**. Record `basis: declaration_only_exclusion`; `run.conformance` becomes `CONFORMANT_WITH_DECLARED_EXCLUSIONS`. |

- The evaluation returns `effective_result` (TRUE/FALSE/UNKNOWN) and `conflict` (bool) **independently**.
- Predicates are limited to the fixed set listed in `predicates.yaml`; arbitrary code cannot be written.

### 2.2 Test Definitions `tests/defs/*.yaml`

```yaml
id: IIP-MD04-02
obligation: IIP-MD04.a                 # ★ Refers to an obligation, not a requirement
title: "Reject metadata whose validUntil has already passed"
title_ja: "Reject metadata whose validUntil has already passed"

mode: CONFIG                           # AUTOMATED | BROWSER | ATTESTED | CONFIG
configuration_failure_semantics: normative_capability
  # normative_capability = The ability to configure it is itself an obligation (no capability → outcome: violated)
  # test_precondition    = Configuration is only a precondition for the test (no capability → outcome: not_verified)
  # See the common determination procedure in [03 §4].
security_relevant: false

requires:
  plan_options:
    suite_metadata_delivery: [http_url, mdq]
    # If not satisfied, use NOT_VERIFIED(plan_configuration); do not use NOT_APPLICABLE.
  reachability: target_to_suite        # Reachability must be confirmed ([07 §2])
  passed_cases: [IIP-SSO01-01]
  session: none                        # none | required | any
  destroys_session: false

setup:
  suite_metadata_variant: expired-valid-until
  parameters:
    expired_by_seconds: 86400

instructions:
  en: |
    1. In your product, force a refresh of the Suite's metadata.
    2. Then attempt a login through this Test Plan.
  ja: |
    1. On the target product, force a refresh of the Suite’s metadata.
    2. Then attempt a login through this Test Plan.

expected:
  en: >
    The target must refuse to load or use the expired metadata. Evidence: a SAML Status
    error, an HTTP 4xx/5xx, or an operator-confirmed error condition. Successfully
    completing SSO while the expired metadata is published is a failure of this obligation.

evidence_ladder: [L1, L2, L4]

attestation:
  question_en: "Did the target refuse the metadata (error shown, no SSO)?"
  options:
    - { value: refused,  outcome: satisfied }
    - { value: accepted, outcome: violated }
    - { value: unclear,  outcome: indeterminate }

implementation: org.samlier.tests.md.ExpiredValidUntilCase
tags: [metadata, trust]
```

### 2.3 ★ Cases Do Not Return Verdict Directly

Test definitions and case implementations return **`outcome` (whether the obligation was satisfied)**,
not `PASS` / `FAIL` / `WARNING`.

**Level normalization** (folding the eight schema-allowed values into three classes):

| `level` values | Class | Basis |
|---|---|---|
| `MUST`, `MUST_NOT`, `REQUIRED` | `MUST_CLASS` | RFC2119 §1, §2, §3 — absolute requirements |
| `SHOULD`, `SHOULD_NOT`, `RECOMMENDED`, `NOT_RECOMMENDED` | `SHOULD_CLASS` | RFC2119 §3, §4 — recommendations |
| `MAY`, `OPTIONAL` | `MAY_CLASS` | RFC2119 §5 — optional |

`MUST_NOT` / `SHOULD_NOT` / `NOT_RECOMMENDED` are **prohibitive** obligations.
`outcome: satisfied` means “did not exhibit the prohibited behavior.”
The case implementation reverses the polarity; by the time `outcome` is reached,
it has already been normalized to “whether the obligation was satisfied.”

**Conversion table**:

| `outcome` | `MUST_CLASS` | `SHOULD_CLASS` | `MAY_CLASS` |
|---|---|---|---|
| `satisfied` | `PASS` | `PASS` | `PASS` |
| `satisfied_with_note` | `WARNING` | `WARNING` | `WARNING` |
| `violated` | `FAIL` | `WARNING` | `NOT_SUPPORTED` |
| `indeterminate` | `INDETERMINATE` | `INDETERMINATE` | `INDETERMINATE` |
| `inconsistent` | `INCONSISTENT` | `INCONSISTENT` | `INCONSISTENT` |
| `not_verified(r)` | `NOT_VERIFIED(r)` | `NOT_VERIFIED(r)` | `NOT_VERIFIED(r)` |

★ `outcome: violated` + `reason_code: capability_absent` (missing configuration capability)
also **follows this table exactly**: FAIL for MUST, **WARNING for SHOULD**, and NOT_SUPPORTED for MAY.
Even when the common determination procedure is used, Evaluator always performs the conversion ([03 §4](03-test-model.md)).

- `satisfied_with_note` means “the obligation was satisfied, but there is an operational note.”
  It is the only route by which a MUST obligation receives `WARNING`, and corresponds to
  `satisfied(o) ≡ verdict ∈ {PASS, WARNING}` in [03 §7.2](03-test-model.md).
- `violated` in `MAY_CLASS` becomes `NOT_SUPPORTED` because the optional feature is simply not implemented.

The Runner centrally converts to Verdict by consulting the level in `coverage.yaml`
(`Evaluator` in [03 §7.5](03-test-model.md)).
**Do not let case implementations decide FAIL/WARNING**; this structurally prevents bugs
such as “the source says SHOULD, but the test returns FAIL.”

## 3. Specification Catalog

Manage `spec.document` values centrally in a separate file, keeping URL and title changes in one place.

```yaml
# tests/specs.yaml
kantara-fedinterop-impl:
  title: "SAML V2.0 Implementation Profile for Federation Interoperability"
  publisher: "Kantara Initiative"
  versions:
    "1.1":
      date: 2019-12-18
      url: https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html
      anchor_pattern: "#{anchor}"
oasis-saml-core:
  title: "Assertions and Protocols for the OASIS SAML V2.0"
  ...
```

## 4. Java Implementation Interfaces

### 4.1 ★ Cases Must Support Suspension and Resumption

During execution, a case enters `WAITING_BROWSER` / `WAITING_CONFIG` / `WAITING_ATTEST`.
Resumption occurs on a **separate HTTP request** and may follow a Suite restart.
Because the Java call stack cannot be preserved across that interval,
a synchronous API such as `CaseOutcome execute(ctx)` cannot work.

Use explicit state transitions.

```java
public interface TestCaseImpl {
    /** Must match the YAML id; verified in CI. */
    String id();

    /** The first step. */
    CaseStep start(CaseContext ctx);

    /**
     * Resume after suspension. state was persisted in SQLite.
     * Repeated calls with the same (state, event) must produce the same result (idempotent).
     */
    CaseStep resume(CaseContext ctx, CaseState state, CaseEvent event);
}

/** What to wait for next. */
public sealed interface CaseStep {
    record Continue(CaseState next)                                   implements CaseStep {}
    record AwaitBrowser(CaseState next, URI startUrl, Duration ttl)   implements CaseStep {}
    record AwaitConfig(CaseState next, String instructionKey, Duration ttl) implements CaseStep {}
    record AwaitAttestation(CaseState next, String questionKey, Duration ttl) implements CaseStep {}
    record AwaitInbound(CaseState next, InboundMatcher m, Duration ttl) implements CaseStep {}
    record Finish(CaseOutcome outcome)                                implements CaseStep {}
}

/** An event received by the Suite. */
public sealed interface CaseEvent {
    record InboundMessage(InboundSamlMessage msg)  implements CaseEvent {}
    record BrowserReturned(String acsPath)         implements CaseEvent {}
    record ConfigConfirmed()                       implements CaseEvent {}
    record Attested(String value, String note)     implements CaseEvent {}
    record TimedOut(Duration waited)               implements CaseEvent {}
    record Aborted(String reason)                  implements CaseEvent {}
}

/** State carried by the case; serialize as JSON and store in SQLite. */
public record CaseState(String phase, Map<String, Object> data) {}

/** The case returns obligation satisfaction, not a Verdict (§2.3). */
public record CaseOutcome(
    Outcome outcome,                 // satisfied | violated | indeterminate | inconsistent | not_verified
    String notVerifiedReason,        // Required when outcome == not_verified
    String reasonCode,               // Machine-readable; e.g. "metadata.accepted-expired"
    String reasonMessageKey,
    List<EvidenceRef> evidence,
    Map<String, Object> details
) {}
```

### 4.2 Implementation Rules

| Rule | Reason |
|---|---|
| `CaseState` contains only JSON-serializable values | Survive restarts |
| `resume` is idempotent | Remain safe under resends, retries, and double-clicks |
| Every `AwaitXxx` has a TTL | Fall back to `NOT_VERIFIED(timeout)` on timeout |
| **Do not execute side effects directly; put them in `CaseStep` as “send intents”** | Crash consistency (§4.4) |
| Do not create `CaseOutcome` except in `Finish` | Keep one determination point |
| Case implementations do not directly use `System.currentTimeMillis()` | Manipulate time in clock-skew tests |

### 4.3 ★ Crash Consistency: Send Through the Outbox

Declaring `resume` “idempotent” alone cannot prevent duplicate sends.
If the process crashes **immediately after sending and before saving state**, the resumption behavior is undefined.

Therefore, **case implementations do not send themselves**. They return send intents, and the Runner executes them through the outbox.

```java
public sealed interface CaseStep {
    /** Transition with send intents; next and actions are persisted in one transaction. */
    record Continue(CaseState next, List<OutboundAction> actions) implements CaseStep {}
    record AwaitBrowser(CaseState next, List<OutboundAction> actions, URI startUrl, Duration ttl) implements CaseStep {}
    ...
}

public record OutboundAction(
    String actionId,              // Deterministically derived from state (no random values or time)
    OutboundKind kind,
    byte[] payload,
    URI target,
    boolean requiresEphemeralCredential   // §4.3.2
) {}
```

Runner execution order:

```
① BEGIN TRANSACTION
     - update case_state to next
     - insert actions into the outbox as (actionId, PENDING)
   COMMIT                       ← “Intended to send” is committed
② Update outbox PENDING to SENDING, then send
③ Send complete → SENT (with send result and Transcript reference)
```

### 4.3.1 ★ Exactly-Once Cannot Be Guaranteed — Use `UNKNOWN_DELIVERY`

The previous version said that resending with the same SAML `ID` would avoid replay detection,
but **the opposite is true**. Resending the same `ID` is precisely what replay detection targets,
and a correctly implemented target **rejects** the second message.
In other words, the more correct the target, the more likely a resend is to fail.

Because network transmission and the `SENT` update cannot be atomic,
**a state in which delivery is unknown necessarily exists**. Represent it as a type.

```
PENDING           Not yet sent              → Safe to send
SENDING           Sending started           → After a crash, move to UNKNOWN_DELIVERY
UNKNOWN_DELIVERY  Delivery unknown  ★       → Follow the rules below
SENT              Send completion confirmed → Do not resend
```

Recovery rules from `UNKNOWN_DELIVERY`:

| Situation | Handling |
|---|---|
| The case is designed to await inbound data (`AwaitInbound` / `AwaitBrowser`) | **Wait first**. If a response arrives from the target, delivery is confirmed and finalized as `SENT`. If none arrives within the TTL, proceed to the next row. |
| ★ `OutboundKind` is in the **Runner’s resendable allowlist** | Resend (below). |
| Otherwise | **Do not resend**. End the case with `NOT_VERIFIED(delivery_unknown)`. |
| After a resend, the target returns a **replay error** (`urn:oasis:names:tc:SAML:2.0:status:Requester`, etc., plus a reason indicating replay) | ★ **Do not treat this as target non-conformance**. The case is `NOT_VERIFIED(delivery_unknown)`. **A Suite-side incident must not become a target FAIL.** |
| The user can confirm the situation (during interaction) | Ask “Was this operation executed on the target?” and branch on the answer. |

#### Do Not Let Cases Declare Resendability

The previous version required `replay_safe: true` in test definitions, but
**CI cannot prove side effects on the target** (static analysis cannot establish that
“only genuinely idempotent operations are performed”). If a case author adds it incorrectly,
the Suite could damage the target’s state and display the target as FAIL.

The Runner fixes resendability **per `OutboundKind`**. Cases cannot choose it.

```java
enum OutboundKind {
    METADATA_FETCH   (Retry.SAFE),    // GET; does not change target state
    MDQ_FETCH        (Retry.SAFE),    // Same
    AUTHN_REQUEST    (Retry.UNSAFE),  // Target may detect replay
    LOGOUT_REQUEST   (Retry.UNSAFE),  // Damages the session
    ECP_SOAP         (Retry.UNSAFE),  // Authentication attempt; lockout risk
    SOAP_SLO         (Retry.UNSAFE);
}
```

- `Retry.SAFE` is limited to **HTTP GETs that carry no SAML state**.
  Adding `SAFE` to a new `OutboundKind` requires code-review approval.
- For `Retry.UNSAFE` `UNKNOWN_DELIVERY`, **do not resend**; use `NOT_VERIFIED(delivery_unknown)`.
- When in doubt, use `UNSAFE`. One case becoming unverified is far preferable to damaging the target.

- A Run in which `UNKNOWN_DELIVERY` occurs is recorded in `suite_incidents[]` in the result JSON.
  Keep it **separate from the target evaluation**.
- Derive `actionId` from `runId + caseId + state.phase + sequence number`.
  `UUID.randomUUID()` / `System.nanoTime()` are prohibited by static analysis (CI rule 26).
- Derive the SAML message `ID` from `actionId` as well.
  The purpose is **not to avoid replay detection**, but to make it possible to identify from target logs
  that the same action was sent twice.

> The goal is not exactly-once, but **“do not transfer uncertainty to target non-conformance.”**
> The worst failure is for the Suite to display someone else’s product as FAIL because of its own incident.

### 4.3.2 ★ Reconciling a Persistent Outbox with Ephemeral Credentials

`OutboundAction.payload` is persisted in the outbox.
ECP HTTP Basic credentials (IIP-IDP14), however, **must not be stored**
([02 §5.2](02-architecture.md), [08 §4](08-suite-security.md)).
As written, putting credentials in the payload violates the storage prohibition,
while omitting them prevents PENDING actions from running after a restart. **The two requirements were incompatible.**

Solution: **do not put credentials in the payload; inject them at execution time.**

```java
// The payload contains only a credential placeholder
OutboundAction(actionId, ECP_SOAP, soapEnvelopeBytes, idpSoapEndpoint,
               requiresEphemeralCredential = true)
```

| State | Behavior |
|---|---|
| Credentials are in memory (within the same process) | The Runner injects them immediately before sending and executes. |
| **Credentials are absent after restart** | Set the action to `BLOCKED_ON_CREDENTIAL` and transition the Run to **`WAITING_CREDENTIAL`**. The UI requests re-entry. |
| The user refuses re-entry / TTL expires | End the case with `NOT_VERIFIED(credential_unavailable)`. |

- Add `WAITING_CREDENTIAL` to the state machine as one of the `WAITING_*` states ([03 §8](03-test-model.md)).
- Keep credentials **only in Run-scoped memory**; write them to neither `CaseState`, the outbox,
  nor the Transcript.
- **Do not adopt** the encrypted secret-store proposal.
  Because the key would also reside under `/data`, this is effectively equivalent to plaintext storage ([08 §4](08-suite-security.md)).
- `CredentialLeakTest`: after restarting across a Run into which credentials were entered,
  verify that **the credentials do not occur in any byte sequence under `/data`**.

### 4.4 CaseContext

Tests must not call HTTP directly. Record everything.

```java
interface CaseContext {
    SamlMessageBuilder authnRequest();      // OpenSAML-based (normal path)
    RawMessageBuilder  rawMessage();        // Direct DOM (foundation for Phase 4)
    MetadataControl    metadata();          // Variant switching and MDQ response control
    HttpExchange       fetch(URI uri);      // Passes SSRF guard; everything is recorded
    EcpClient          ecp();               // PAOS/SOAP client ([02 §4])
    Clock              clock();
    PlanParameters     params();
    Reachability       reachability();      // Whether target→suite is confirmed ([07 §2])
    Transcript         transcript();
}
```

## 5. Consistency Enforced in CI

```
[Catalog]
 1. Every requirement id in coverage.yaml is a known id in the specification catalog, and all 69 are present
 2. Every obligation key is unique and has the form `<requirement>.<a|b|c…>`
 3. Every obligation has roles / level / summary_en
 4. level is one of MUST|MUST_NOT|REQUIRED|SHOULD|SHOULD_NOT|RECOMMENDED|MAY|OPTIONAL
 5. Each condition predicate belongs to the defined set in `predicates.yaml`
 5b. Every condition contains `predicate_kind`
 5b-1. A `CAPABILITY_BASED` condition has **non-empty `observed`**
 5b-2. A `CLASSIFICATION_BASED` condition with `level ∈ MUST_CLASS` has a
       `declaration_only_exclusion` block (`allowed` / `requires_reason` / `statement_en`)
 5b-3. **[:specReconcile]** `CLAIM_BASED` may be used only for obligations whose
       **`source_clause`-designated phrase** contains language equivalent to *claim(s) support*
       (checking the whole section would miss misuse for another obligation in the same section)
       (Confirm that the term occurs in the relevant source section retrieved by `:specReconcile`.
        A digest is a hash and cannot be used to inspect terms.)
 5b-4. ★ **[:specReconcile]** For every obligation with an exclusion predicate (`CLASSIFICATION_BASED`),
       the obligation’s `source_clause` or the **end of the same section** contains an exclusion statement
       (prevents the exclusion scope from extending to adjacent requirements; [03 §1](03-test-model.md))
 5c. No predicate absent from `predicates.yaml` is used
 5d. ★ Every `mode: CONFIG` case has `configuration_failure_semantics`
     (`normative_capability` | `test_precondition`) ([03 §4](03-test-model.md))
 6. A testability NOT_OBSERVABLE obligation has not_observable_reason_en
    and **has no test cases**
 6b. Every obligation has a review block, whose `state` is **always `PENDING_REVIEW`**.
     ★ **Do not write approvals in `coverage.yaml`**. The canonical source is the signed `tests/approvals/g1.yaml`
     (outside the approved commit); `reviewer` / `approved_at` go there.
     （[03 §7.5](03-test-model.md), [tools/ci-stages.md](../tools/ci-stages.md)）
[Catalog continued]
 6c. Every obligation has `authored_by` and `review.{state, reviewer, approved_at, source_spec,
     spec_version, source_selector, source_section_digest}`.
     Clause locations are held in **`source_clauses[]` (start / end / digest / occurrences)** directly under the obligation
     (multiple ranges are allowed; shared lead-ins and individual items can be recorded in separate ranges).
 6c-0. Every element of `source_clauses[]` satisfies `0 ≤ start < end` (non-empty, in Unicode code points).
 6c-2. Each `source_clauses[]` `occurrences` is 1. If 2 or more, the locator is ambiguous;
       make the clause unique or specify its occurrence in the filing system (`g1_validate.py` SR-11 / SR-12).
       ★ **`end ≤ section length` cannot be checked offline** (the source is not kept in the repository,
       so its length is unknowable). This check is performed by the :specReconcile side of 6c-1.
 6c-1. **[:specReconcile only; network required]** The normalized digest of the section selected by source_selector
       matches source_section_digest, **`end ≤ section length`**, and
       **the digest of the substring in the source_clause range matches source_clause.digest**
       (approval is invalid if any check fails).
       Retrieve the source into build/spec-cache/ without storing it in the repository
       (to comply with the prohibition on reproducing the full text in [09 D-11](09-open-decisions.md); [04 G1](04-requirement-coverage.md)).
 6d. review.spec_version matches the current version in specs.yaml
 7. Every obligation whose testability is not NOT_OBSERVABLE has at least one case
    (otherwise it fails as a release blocker = eliminate NOT_VERIFIED(not_implemented))

[Test definitions]
 8. Every YAML id is unique
 9. Every YAML obligation exists in coverage.yaml
10. Every YAML implementation class exists, implements TestCaseImpl, and has a matching id()
11. Every TestCaseImpl implementation has a corresponding YAML (orphan implementations prohibited)
12. Cases with mode: ATTESTED or L4 in evidence_ladder have an attestation block
13. attestation.options outcomes are Outcome values
14. Every case has instructions.en / expected.en (ja is optional)
15. requires.passed_cases contains no cycles
16. **Test definitions contain no level / verdict** (determination levels exist only in coverage.yaml)

> Rules marked **[:specReconcile]** run in a **separate networked job**.
> Keep routine `./gradlew check` offline, and always run `:specReconcile` in scheduled CI jobs
> and before release ([04 G1](04-requirement-coverage.md)).

[Determination logic]
17. VerdictAggregationTest: all 10 × 10 combinations match the decision table in [03 §6.4](03-test-model.md)
18. RunVerdictTest: for every combination of FAIL / NOT_VERIFIED / NOT_OBSERVABLE / INCONSISTENT,
    the Run determination matches [03 §7.2](03-test-model.md)
19. OutcomeToVerdictTest: the outcome × level → Verdict mapping matches [§2.3](#23-★-cases-do-not-return-verdict-directly)
20. NotApplicableGuardTest: guarantee through code paths that **NOT_APPLICABLE arises only from role mismatch
    or a false condition of a conditional obligation**
20b. ★ **CapabilityBranchTest**: every `mode: CONFIG` case passes through
     the [common determination procedure in 03 §4](03-test-model.md). Cases return **outcome**,
     and `capability_absent` is the reason_code for `outcome: violated`
     (for `normative_capability`). Guarantee at compile time that case implementations have no type returning Verdict,
     and prohibit paths that independently return a not_verified variant through static analysis.
20c. Verify with table-driven tests that `outcome: violated` + `reason_code: capability_absent`
     converts according to `obligation.level` to **MUST→FAIL / SHOULD→WARNING / MAY→NOT_SUPPORTED**
     (**do not turn SHOULD into FAIL**).
20d. When a conditional obligation has `effective_result == FALSE`, its case is not executed
     (applicability is evaluated before case execution; e.g. IIP-ALG05.b is NOT_APPLICABLE when CBC is unsupported).

29. Release tasks (`release` / `publish` / `dockerPush`) depend on `:specReconcile`
    and accept only reports generated by its execution
    ([04 G1](04-requirement-coverage.md); do not rely on operational conventions).

[Generated artifacts]
21. docs/04-requirement-coverage.md matches the content generated from coverage.yaml
    (fail if manual edits are detected)
22. Aggregate values (such as Phase 1 implementation counts) are generated only; no hand-written numbers remain in documents
23. **The result.json example in the documentation matches Evaluator output**
    (golden fixture; hand-written JSON examples prohibited; [03 §7.5](03-test-model.md))
24. result.json conforms to the JSON Schema (`schema/result-v1.json`)
24b. **`advisories[].affects_verdict` cannot take a value other than `false`** (fixed by the schema).
     Advisories affect neither Verdict, coverage, nor conformance.
25. The golden fixture’s Run determination matches the rules in [03 §7.2](03-test-model.md)
    (e.g. unresolved MUSTs do not produce a CONFORMANT result, and SP-only obligations do not appear in an IdP Run).
26. **Outbox rule**: `OutboundAction.actionId` is deterministically derived from state
    (`UUID.randomUUID()` / `System.nanoTime()` are prohibited by static analysis)
27. Case implementations issue no HTTP through any path other than `ctx` (sending is outbox-only)
27b. The payload of an action with `requiresEphemeralCredential` contains no credentials
     (`CredentialLeakTest`; §4.3.2)
27c. Test definitions contain no field equivalent to `replay_safe`
     (resendability is determined solely by the `OutboundKind` allowlist)
28. **Dependency specification versions are fixed**: every specs.yaml entry has version / date / URL,
    and obligations referencing external drafts (such as SAML-EC) specify that version.
```

> 17–20 provide structural safeguards against [review findings 1 and 2](10-memo-review.md).
> Merely writing rules in a document will inevitably lead to implementation drift.

## 6. Publishing Test Definitions

Test definitions are included in the Suite repository and are readable from the Web UI.
Link each item on the results page to the full YAML for that test.

> This is the central means of gaining trust “without claiming to be a certification body.”
> Anyone who questions a determination must be able to **read in full what was tested and on what basis**.
