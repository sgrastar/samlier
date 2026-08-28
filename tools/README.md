# tools/

Tools for the G1 design gate. **Generation, documentation, and validation are separated into three components.**

| Script | Role | Network | Authoring input | Reproducibility |
|---|---|---|---|---|
| `g1_author.py` | **Generates** `tests/*.yaml` from source text | Not required (reads `build/spec-cache/`) | **Required** (gitignored) | Initial drafting only |
| `g1_docgen.py` | `tests/coverage.yaml` → `docs/04` | **Not required** | **Not required** | ✅ Identical output from a separate checkout |
| `g1_validate.py` | **Independently validates** committed artifacts | Required (`--offline` can use the cache) | **Not required** | ✅ |

Dependency: `PyYAML` (`g1_docgen.py` / `g1_validate.py`)

```bash
python3 tools/g1_docgen.py            # Regenerate docs/04
python3 tools/g1_docgen.py --check    # Only check that it matches the generated artifact (for CI)
python3 tools/g1_validate.py          # Fetch and reconcile source text → build/spec-reconcile-report.json (not tracked by Git)
python3 tools/g1_validate.py --offline
```

## The validator is independent of generation

`g1_validate.py` **only reads and reconciles committed `tests/*.yaml`**; it writes no values back at all. In particular:

- It **compares the source digest with the recorded value in `specs.yaml`**
  (if the value were written at generation time, a changed source would also change the recorded value and validation would be meaningless).
- It **recomputes** section and clause digests, non-normative spans, and offset ranges, then compares them with the recorded values.
- It counts **clause occurrences** and detects multiple matches (ambiguous locators).
- It checks whether `predicates.yaml` has `observed` and whether `configuration_failure_semantics` is explicit.

> Immediately after this separation was introduced, the validator detected that `IIP-EXT01.b` / `.c` **pointed to the same string**.
> The defect went unnoticed while generation and validation used the same code.

## Reproducibility

`docs/04` can be generated from **`coverage.yaml` alone**, and `g1_docgen.py --check` can verify that it matches.
Regenerating `coverage.yaml` itself requires authoring input (not distributed because it contains source-text clauses),
but **`g1_validate.py` independently verifies its correctness against the source**, so validation is complete using only the distributed files.

## `g1_authoring.py` (gitignored)

Contains **the source-text clause itself** corresponding to each obligation. It is not distributed to remain compatible with
[docs/09 D-11](../docs/09-open-decisions.md) (“do not reproduce source text”). Only offsets and digests remain in `coverage.yaml`.
