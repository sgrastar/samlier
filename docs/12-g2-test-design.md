# 12. G2 Test Design

G2 converts the approved obligation catalog into implementation-ready case
designs. It does not implement verdict cases and does not change G1 artifacts.
The canonical design is `tests/cases.yaml`; `tools/g2_validate.py` validates it
independently and writes the untracked `build/g2-report.json`.

## Design units

- Every obligation other than `NOT_OBSERVABLE` has one case for every role in
  its approved `roles` list.
- Every case covers its complete stable-ID variant set plus transitive
  `linked_obligations` expansion. The owner supplies role, level, testability,
  and normally applicability; an explicit `linked_condition` applies only to
  imported variants.
- `variant_plan` binds each reference to the exact approved G1 instruction and
  runtime applicability. `variant_groups` distinguish `all_of`, `one_of`, and
  `one_of_available`, so an OR in the source cannot become an AND just because
  both alternatives appear in the catalog.
- A mutant whose trigger belongs to a multi-member `one_of` group declares the
  complete group in `executor.mutation_variants` and makes all alternatives
  nonconforming together. Flipping only one permitted alternative cannot prove
  that an OR obligation was violated.
- Every evaluative case has positive and negative controls. The positive fixture
  is its baseline and the negative fixture is its role-specific mutant. A failed
  control becomes `NOT_VERIFIED(control_failed)`, never a target violation.
- An explicitly non-evaluative MAY/OPTIONAL choice has an informational fixture
  and reviewed mutant waiver. G2 never fabricates an unreachable `violated`
  outcome for a behavior where all listed choices conform.
- Cases contain no level- or Verdict-computing fields. Guidance copied verbatim
  from approved G1 `controls` or `notes_en` may mention RFC 2119 levels or
  Verdict names, but cannot compute or return them. Evaluator remains the only
  outcome-to-Verdict boundary.

## Detection-power oracle

The oracle uses five fixed scenarios: IdP and SP Core and Full baselines plus a
separate non-SAML-upstream proxy IdP. This prevents mutually exclusive target
classifications from being asserted in one fixture. All scenarios include the
unconditional MDQ acquisition capability.

Baseline outcomes use one fixed precedence:

1. role mismatch → `not_applicable(role_mismatch)`;
2. a Full obligation omitted by a Core profile →
   `not_verified(profile_not_selected)`;
3. a false condition on an in-profile obligation →
   `not_applicable(condition_false)`;
4. otherwise, the baseline behavior is `satisfied`.

The validator independently derives condition results from declared features,
target classification, and Suite-controlled input, and compares them with both
the baseline and its concrete fixture.

Each role-specific mutant has a structured observation contract: evidence kind,
observation surface and signal, conforming and mutated evidence, the unavailable-
evidence outcome, and an isolation rule. `unchanged_required: all_others` means
the mutation may change only its named obligation. The M1 adapter must execute
this contract; an absent observation produces `not_verified`, not a violation.

`reject-everything` and `accept-everything` are control mutants only for cases
whose outcome can actually change through receiver rejection or acceptance.
Producer-side, passive, CONFIG, ATTESTED, and informational cases are excluded
unless their approved trigger itself describes a receiver decision.

## Session effects

Every case that needs authenticated SLO state receives a fresh isolated session
from `tests/fixtures/session/fresh-authenticated-session.yaml`. A destructive
case must state why its successful flow terminates or invalidates that session.
Response evidence is recorded before destruction, and the session is never
reused. A mere mention of LogoutRequest or LogoutResponse is not enough to mark
a case destructive.
Target-initiated SLO observations also receive a fresh session even when the
case is not destructive; without authenticated state, a target cannot emit the
LogoutRequest whose behavior the case is intended to observe.

## Feasibility boundary

`tests/feasibility.yaml` records six architecture spikes. Every assertion maps
one-to-one to a named executable test that calls a production boundary: ECP
header forwarding, the transport-preserving SLO model, stable MDQ variants, the
secondary Test IdP identity helper, byte-preserving raw XML generation, or raw
HTTP-Redirect signature input. These are architectural probes, not verdict
cases or interoperability claims about an external product.

## Approval protocol

The authoring state remains `PENDING_REVIEW`. A reviewer other than
`samlier-g2-builder` reviews target commit C and creates signed commit A whose
sole change is `tests/approvals/g2.yaml`. The approval record binds every case
digest; all G1 inputs; the complete fixture tree; dependency and Gradle inputs;
the production feasibility boundaries; validators, schemas, and CI. Approval
timestamps must lie between the target and approval commit times.

Use the externally pinned verifier after approval:

```bash
G2_TOOLS_COMMIT=<full target SHA> PY=.venv/bin/python tools/g2_ci_verify.sh
```

CI also re-extracts the pinned target and reruns its feasibility tests. M1 case
implementation remains prohibited until the report contains `g2.complete: true`
with `validator_source_kind: external-pin`.

## Local verification

```bash
.venv/bin/python tools/g2_validate.py
./gradlew :saml:test :peer:test --no-daemon
.venv/bin/python tools/g1_validate.py --structural-only
.venv/bin/python tools/g1_language_check.py
git diff --check
```
