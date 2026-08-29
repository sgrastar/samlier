# Separation of CI stages

**Use separate jobs for G1 checks and release CI.**
Test-case implementations do not yet exist during the G1 phase;
putting rules that require case implementations in the same job would make G1 fail forever.

| Stage | When it runs | Network | What it checks |
|---|---|---|---|
| **`g1Check`** | Every PR (current main job) | Not required | English-canonical migration invariants, Japanese-residue scan, generated-document equality, and catalog structure. Rules 1–6c-0 and 20d of [05 §5](../docs/05-test-definition-format.md) |
| **`specReconcile`** | Scheduled + before release | **Required** | Fetches source text and checks section/clause digests and terminology. Rules 5b-3, 5b-4, and 6c-1 |
| **`releaseCheck`** | Before `release` / `publish` / `dockerPush` | Not required | **Rules requiring case implementations**: 7–19, 20b–20c, 21–28 |

```
tasks.named("release")    { dependsOn(":specReconcile", ":releaseCheck") }
tasks.named("publish")    { dependsOn(":specReconcile", ":releaseCheck") }
tasks.named("dockerPush") { dependsOn(":specReconcile", ":releaseCheck") }
```

## Rules included in `g1Check` (must pass during the G1 creation phase)

- 1–5d: Catalog structure, predicates, conditions, and `configuration_failure_semantics`
- 6b: Every obligation has a `review` block (`state: PENDING_REVIEW` is permitted)
- 6c / 6c-0: Presence and ranges of `source_spec` / `source_selector` / `source_section_digest` / `source_clause`
- 6: `NOT_OBSERVABLE` obligations have a reason statement and no cases
- 20d: Applicability of conditional obligations is evaluated before case execution
- English-canonical migration: semantic equality with baseline commit `ca54c4b83ac1a3208591f03772b4cf52c62045d4`, explicitly reviewed semantic exceptions, one-to-one variant-ID mapping, and no legacy Japanese-language fields
- JSON Schema enforcement for the catalogs, variant-ID map, and semantic-exception manifest
- Public-language policy: no Japanese characters in tracked public text outside the explicit allowlist

## Rules deferred to `releaseCheck` (become effective after G1 is complete)

- ★ **Approval is not checked by looking at `coverage.yaml`**. Run the pinned-SHA `g1_ci_verify.sh`,
  then verify **`g1.complete == true`** and **`provenance.validator_source_kind == "external-pin"`**
  in the generated `build/spec-reconcile-report.json` (`review` in `coverage.yaml` remains `PENDING_REVIEW` at all times).
- 7: Every obligation other than `NOT_OBSERVABLE` has at least one test case
- 8–19: Consistency between test definitions and implementations (YAML ↔ `TestCaseImpl`)
- 20b–20c: `CapabilityBranchTest` / outcome→Verdict conversion
- 21–28: Generated-artifact equality, golden fixture, outbox rules, and dependency-spec version pinning

## Actual implementation (`.github/workflows/g1.yml`)

| job | trigger | Network | Content |
|---|---|---|---|
| `g1-check` | PR / push | Not required | Migration comparison + variant map + schema enforcement + Japanese-residue and legacy-field scan + `g1_docgen.py --check` + **`--structural-only`** |
| `spec-reconcile` | push / scheduled / manual | **Required** | Force-fetches and reconciles the source text and all <!--g1:specs-->25<!--/g1--> specifications |
| `g1b-approval` | **Always runs** (no job condition) | Required | Validates signed approval. **Extracts the runner and dependencies from the pinned SHA and runs them in isolation**, then checks `g1.complete` / provenance / pin equality |

`g1b-approval` **does not call `tools/g1_ci_verify.sh`; it expands the equivalent process in the workflow**.
Because the wrapper itself can be modified, **putting this in the CI configuration is the final trust anchor**.

Required repository variables:

| Variable | Content |
|---|---|
| `G1_TOOLS_COMMIT` | Source for runner / validator / dependencies (complete 40-digit SHA). **Set this at approval time.** |
| `G1_ALLOWED_SIGNERS` | Contents of `gpg.ssh.allowedSignersFile` (approvers' public keys). ★ Pass through `env:` (directly expanding `${{ }}` into `run` causes script injection) |
| `G1_SIGNER_MAP` | Optional external fixed mapping of `principal=reviewer-id,...`. If unset, **reviewer must equal the signer's principal** |

### Do not toggle enablement with a job condition ★

A job skipped by a condition is treated as **Success** by GitHub,
and does not block merging even when made a required check.
Adding a condition such as `if: vars.G1B_ENABLED == 'true'` would let the gate be disabled
**simply by deleting the variable**.

`g1b-approval` **always runs** and **fails** if approval is incomplete.
Before G1b, this job being red is the correct state;
**whether to make it a required check is switched on the branch-protection side**.

**Protect `.github/` and `tools/g1_*` with `.github/CODEOWNERS`,
and require “CODEOWNERS review” in branch protection.**
Without this, rewriting the workflow alone disables the entire gate.

## Implementation status

| Stage | Actual implementation | Status |
|---|---|---|
| `g1Check` | migration comparison + JSON Schema enforcement + language/legacy-field checks + structural checks + `g1_docgen.py --check` | Passes |
| `specReconcile` | `tools/g1_validate.py` (**force-fetches** and reconciles source text and all <!--g1:specs-->25<!--/g1--> specifications) | **`totals.blocking_failures == 0`** (before approval, SR-30 / SR-31 remain FAIL). ★ Do not hard-code a PASS count because it changes whenever checks are added |
| `releaseCheck` | Not implemented because there are 0 test cases (after G2 is complete) | Not run |

`checks[]` in `build/spec-reconcile-report.json` distinguishes blocking checks using
`totals.blocking_failures`. **SR-30 (open question remains) and SR-31 (not approved)
are completion conditions for G1** and remain FAIL when submitted during the creation phase.
Any other FAIL indicates a defect in the artifacts, and `g1_validate.py` returns exit code 1.

### ★ Approval is bound to a signed record outside the target commit

`obligation_digest` detects accidental modification, but is powerless against **someone who can recompute the digest**.
Therefore, approval authenticity is guaranteed **only by the git signature**.

```
commit C : tests/{coverage,specs,predicates}.yaml     ← Approval target (all PENDING_REVIEW)
commit A : tests/approvals/g1.yaml                    ← Approval record. ★ Outside C; signature required
           (do not edit coverage.yaml)
```

**Do not place the approval record inside the approval target.** Appending the record changes the target commit,
so it can never match under the normal procedure (self-reference).

`tests/approvals/g1.yaml`:

```yaml
target_commit: <40-digit complete SHA-1>          # Reject abbreviated SHAs
artifact_digests:
  tests/coverage.yaml:   "sha256:…"          # Digest of the target commit's contents
  tests/specs.yaml:      "sha256:…"
  tests/predicates.yaml: "sha256:…"
evidence:
  kind: signed-commit
  reviewers: [<approver>]
  evidence_url: https://…                     # PR / review record
  # Do not place ref here. Writing a commit SHA that includes itself is self-reference.
approvals:
  - obligation: IIP-G01.a
    obligation_digest: "sha256:…"             # Recomputed from the target commit's contents
    reviewer: <approver>                         # Must differ from authored_by
    approved_at: 2026-08-26T12:00:00+00:00    # ISO-8601 with required time zone
```

The validator (**SR-38**) verifies:

| Check | Method |
|---|---|
| Approval record is committed | `git log -1 -- tests/approvals/g1.yaml` |
| **Current values of protected files match A** | `git show <A>:<path>` and byte comparison |
| **Every explicitly protected G1 path matches A** | `git show <A>:<path>` and byte comparison |
| **That commit is signed** | `git verify-commit` |
| **The canonical copy is the signed commit's contents** (not the working tree) | `git show <C_sig>:tests/approvals/g1.yaml` |
| Working tree matches signed contents | Digest comparison |
| `target_commit` is 40 digits and exists in git | Exact match from `git rev-parse --verify <sha>^{commit}` |
| Target commit artifact digests match | `git show <C>:tests/*.yaml` |
| All obligations are approved and digests match target commit contents | Recomputed from target commit |
| reviewer ≠ authored_by / is included in `evidence.reviewers` | Compared with `authored_by` from target commit |
| `approved_at` is ISO-8601 with a time zone | Entire string passed to `fromisoformat` |

### Protected by approval (`PROTECTED_PATHS`)

Signing only the approval record is meaningless. These are **compared between signed A's tree and current values**:

```
tests/coverage.yaml      tests/specs.yaml       tests/predicates.yaml
tests/approvals/g1.yaml  tools/g1_validate.py   tools/g1_extract.py
tools/g1_migration_validate.py  tools/g1_schema_validate.py  tools/g1_language_check.py
tools/g1-semantic-exceptions.yaml  schema/g1-*.json
```

The approval boundary is deliberately path-scoped. Later-gate artifacts such as
`tests/cases.yaml`, `tests/mutants/*.yaml`, and `tools/g2_validate.py` may coexist
without invalidating G1. The three normative catalogs and every G1 verifier,
schema, exception manifest, and approval record remain explicitly protected.

Direct validator execution restarts with `python -I` before importing PyYAML and
removes its own `tools/` directory from `sys.path`. This prevents a later-stage
module such as `tools/yaml.py` from shadowing a trusted dependency. Whole-directory
file-set equality is therefore neither the trust boundary nor a substitute for
the explicit protected-path list.

**The validator itself is included among the protected paths**, but that is not sufficient.
**If a modified validator is executed, it will not report its own modification** (the limit of self-inspection).

### ★ Validate approval through `tools/g1_trusted_verify.py`

```bash
python3 tools/g1_trusted_verify.py [--offline]
#   0 = no blocking violations / 1 = present / 2 = verification preconditions are broken
```

This runner **does not execute the validator from the current checkout**.

1. **Relaunches itself with `python -I`** (disabling `PYTHONPATH` and the user site).
   Without this, `PYTHONPATH=. python tools/g1_trusted_verify.py` would execute an unsigned `yaml.py` in the repository root **before** signature verification.
2. Identifies commit A that last changed the approval record from git.
3. Verifies A's signature (`signed-commit` uses `git verify-commit`; `signed-tag` uses `git verify-tag` and confirms that the tag points to A).
4. Confirms that **A is a descendant of target commit C**.
5. Confirms that **the only change in `C..A` is `tests/approvals/g1.yaml`**.
6. ★ **Does not take the validator from A**. If `G1_VALIDATOR_COMMIT` (a CI-fixed external
   trust anchor; **only a complete 40-digit SHA**; mutable refs such as `HEAD` / `main` are rejected)
   exists, takes it from there; otherwise takes `g1_validate.py` / `g1_extract.py` from **C** (the artifact actually read by the reviewer) into an isolated directory.
7. Executes with `python -I` and passes the repository under test through `G1_REPO_ROOT`.

> **Why it must not be taken from A**: The signer of A could weaken the validator together with the approval record.
> In fact, including a validator in A that “immediately reports PASS for everything and exits” resulted in `exit 0` after signature verification passed.
>
> Taking it from C still leaves room for the signer to create C itself.
> **CI must fix `G1_VALIDATOR_COMMIT` through external configuration.**

### Pin the runner itself — `tools/g1_ci_verify.sh` ★

The constraints inside the runner (the `C..A` restriction and fixed validator source)
**can be removed by rewriting the runner**. The runner cannot prevent this internally.

```bash
G1_TOOLS_COMMIT=<40-digit SHA> tools/g1_ci_verify.sh [--offline]
```

This wrapper extracts `g1_trusted_verify.py` / `g1_validate.py` / `g1_extract.py` from the pinned SHA
and executes them with `python -I`. Because the repository under test is passed through `G1_REPO_ROOT`,
**the real repository**, not the isolated directory, is inspected.

- `G1_TOOLS_COMMIT` unset → **exit 2** (fail closed)
- Mutable refs such as `HEAD` / `main` → **exit 2**
- ★ **The validator source is always `G1_TOOLS_COMMIT`**. Remove any inherited
  `G1_VALIDATOR_COMMIT` with `env -u` (to prevent ambient inheritance from making
  “the runner correct but the validator from another commit”). To use another anchor,
  explicitly specify `--validator-commit=<40-digit SHA>`
  (a warning is printed and recorded in `provenance.validator_source`).
- Observed: Even if the current checkout's runner is rewritten to “immediate PASS”,
  the wrapper returns `BLOCK` (SR-38 / SR-40)

**Fix the final layer in the CI configuration.** Since the wrapper itself is a copy in the repository,
CI must write the snippet below **inline** in the workflow or extract and execute it from a pinned SHA.

```yaml
# Example in .github/workflows, etc.
- name: G1 approval verification
  env:
    G1_TOOLS_COMMIT: "0000000000000000000000000000000000000000"   # ← Fix at approval time
  run: |
    set -euo pipefail
    TMP=$(mktemp -d); mkdir -p "$TMP/tools"
    for f in tools/g1_trusted_verify.py tools/g1_validate.py tools/g1_extract.py; do
      git show "$G1_TOOLS_COMMIT:$f" > "$TMP/$f"
    done
    env -u PYTHONPATH G1_REPO_ROOT="$PWD"         G1_RUNNER_COMMIT="$G1_TOOLS_COMMIT"         G1_VALIDATOR_COMMIT="$G1_TOOLS_COMMIT"         python3 -I "$TMP/tools/g1_trusted_verify.py"
```

The audit report's `provenance` records `validator_source` / `validator_source_kind`
(`external-pin` / `target-commit`) / `runner_source` / `repo_root`.

### Do not edit `coverage.yaml` at approval time

Because `C..A` is restricted to the approval record, **do not edit `g1_state` during approval either**.
Completion status is derived from the approval record (`g1.state` in the report is derived;
`g1.authored_state` is the value recorded in `coverage.yaml`).

**Block shadow imports**: Python puts the script's location in `sys.path[0]`,
so merely placing an untracked `tools/yaml.py` runs arbitrary code before signature verification.
The runner removes its own directory from `sys.path` at startup,
and the extracted validator never puts `tools/` on `sys.path`
(`g1_extract` loads it with `importlib` using an explicit path).
Together with **SR-40** (no untracked or uncommitted `.py` in `tools/`) and the
explicit protected-path comparison, this prevents an unreviewed shim from
participating in G1 verification while allowing committed later-gate tools to coexist.

## G2 design and approval

`tools/g2_validate.py` validates the role-specific case catalog, exact stable-ID
variant expansion, controls, counterexamples, dependency graph, baseline outcome
matrix, mutant all-others oracle, and named feasibility tests. Its report is
`build/g2-report.json` and remains `PENDING_REVIEW` until every case digest is in a
signed `tests/approvals/g2.yaml` record.

The G2 trust protocol mirrors G1: target commit C contains no approval record;
signed commit A adds only `tests/approvals/g2.yaml`; CI extracts the trusted runner
and validator from the immutable `G2_TOOLS_COMMIT`. The workflow must verify
`g2.complete == true` and external-pin provenance before enabling M1. It then
extracts that same immutable target into an isolated directory and reruns the
SAML and peer feasibility tests under Java 21. The signed artifact manifest
includes the approved G1 inputs, the complete fixture tree, build/dependency
inputs, and every production boundary used as feasibility evidence.

**Explicit limits** (the validator makes no stronger claim):

| Can guarantee | Cannot guarantee |
|---|---|
| Holder of the signing key signed the approval record | Whether that key belongs to a **real reviewer** (depends on repository-side configuration such as `allowedSignersFile` / CODEOWNERS) |
| Protected files did not change after approval | If **the runner itself** was modified. → `tools/g1_ci_verify.sh` extracts the runner from a pinned SHA (below) |
| The reviewer **recorded** that they read the source text | That the reviewer **actually** read the source text |

**G1 completion formula** (report's `g1.complete`):

```
complete = (blocking failure = 0)
       AND (open question = 0)
       AND (all obligations approved in tests/approvals/g1.yaml)
# Do not edit coverage.yaml at approval time (it cannot be edited because of the C..A restriction)
```

Simply changing to `state: APPROVED` does not pass. **SR-36** requires
reviewer / approved_at, reviewer ≠ authored_by, and the spec / version / selector / section digest recorded at approval to match current values
(if source text changes, the section digest changes and approval automatically becomes invalid).

## Dependency environment

```bash
python3 -m venv .venv
.venv/bin/pip install -r tools/requirements.txt   # PyYAML 6.0.2 / pdfminer.six 20240706
.venv/bin/python tools/g1_validate.py             # Default = force-fetch (network)
.venv/bin/python tools/g1_validate.py --offline   # Cache only; for CI g1Check
```

## Fetch semantics

`g1_extract.fetch()` modes:

| mode | Behavior | Used by |
|---|---|---|
| `network` (default) | **Always refetches** and updates the cache | `specReconcile` / before release |
| `offline` | Cache only. Fails if not cached | `g1Check` (offline CI) |
| `cache-first` | Uses the cache if available | Drafting (`g1_author.py`) only |

If cache-first were the default, an unreachable **URL could still PASS using stale content**.
The default was changed to force-fetch, and it has been verified in practice that unreachable URLs produce `SR-00` / `SR-33` failures.

## Only pin source text that is reproducible by refetching

`G1_VERIFY_STABILITY=1 python3 tools/g1_author.py` verifies that **two fetches of every pinned specification produce identical bytes**.

The dynamically rendered HTML at `tools.ietf.org` and OASIS errata HTML changed bytes on every fetch,
so their digests could not be pinned. They were therefore switched to **immutable archive URLs
(IETF: `www.ietf.org/archive/id/*.txt`; OASIS errata: PDF)**.
