# 12. G2 Test Design

G2 converts the approved obligation catalog into an implementation-ready case
design. It does not implement verdict cases and does not change the G1 catalogs.
The canonical design is `tests/cases.yaml`; `tools/g2_validate.py` validates it
independently and writes the untracked `build/g2-report.json`.

## Design units

- Every obligation other than `NOT_OBSERVABLE` has a case for each role in its
  approved `roles` list. A shared IdP/SP obligation is therefore not silently
  covered by testing only one side.
- Every case covers the complete stable-ID set from its own
  `required_variants` plus transitive `linked_obligations` expansion.
- The owner supplies role, level, condition, and testability. Imported variants
  apply the link's explicit `variant_applicability` rule.
- Every case has positive and negative controls. The positive control names one
  of the fixed baseline scenarios; the negative control names the case's
  role-specific mutant, so neither control may refer to an undefined fixture.
  A failed control produces
  `control_failed` and ultimately `NOT_VERIFIED(control_failed)`; it never becomes
  a target violation.
- Cases contain outcomes and evidence expectations, never Verdict or RFC 2119
  levels. The Evaluator remains the only outcome-to-Verdict boundary.

## Detection-power oracle

The oracle is a difference from one of four fixed baseline scenarios: IdP and SP,
each in Core and Full form. Every role-specific case points to a target mutant
whose baseline satisfies the obligation, whose expected change is `violated`,
and whose `unchanged_required` value is `all_others`.

The mutation contract is declarative. The role adapter receives the case's
qualified variant IDs, the exact G1 text for its trigger variant, the obligation
mutation point, and an observation contract, then performs the negative fixture
described by the case. A `CONFIG` mutation distinguishes a normative capability
from a test precondition: inability to establish a test precondition produces
`not_verified` and is never mutated into a target violation. This makes the mutation input stable
before M1 without placing case-side Verdict logic in G2. The M1 golden tests must
execute each contract; an unimplemented mutation adapter cannot be reported as a
successful mutant run.

`reject-everything` and `accept-everything` are control mutants. They validate
positive and negative controls respectively; they are not interpreted as
absolute conformance results.

## Feasibility boundary

`tests/feasibility.yaml` records the six architecture spikes required by the
roadmap. The named JUnit test exercises ECP/SAML-EC XML boundaries, SLO and Async
SLO representation and ordering, metadata redirect variants, the secondary Test
IdP identity path, byte-preserving raw XML fixtures, and raw HTTP-Redirect
signature input. These probes show that the selected stack can represent the
design. They are not verdict cases and do not claim interoperability with a
particular external product.

Interactive SLO cases use a fresh session created from
`tests/fixtures/session/fresh-authenticated-session.yaml`. Only cases whose own
successful flow invalidates or terminates that session set `destroys_session`;
merely mentioning a LogoutRequest or LogoutResponse does not imply destruction.

## Approval protocol

The authoring state remains `PENDING_REVIEW`. A reviewer other than
`samlier-g2-builder` reviews the complete target commit C and creates a signed
commit A whose sole change is `tests/approvals/g2.yaml`. The approval record binds
every `case_digest` and every protected G2 artifact.

Use the externally pinned verifier after approval:

```bash
G2_TOOLS_COMMIT=<full target SHA> PY=.venv/bin/python tools/g2_ci_verify.sh
```

M1 case implementation remains prohibited until the resulting report contains
`g2.complete: true` with `validator_source_kind: external-pin`.

## Local verification

```bash
.venv/bin/python tools/g2_validate.py
./gradlew :saml:test --tests org.samlier.saml.G2FeasibilitySpikeTest --no-daemon
.venv/bin/python tools/g1_validate.py --structural-only
.venv/bin/python tools/g1_language_check.py
git diff --check
```
