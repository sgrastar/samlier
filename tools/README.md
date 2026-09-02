# tools/

Tools for the G1 and G2 design gates. **Generation, documentation, and validation are separated.**

| Script | Role | Network | Authoring input | Reproducibility |
|---|---|---|---|---|
| `g1_author.py` | **Generates** `tests/*.yaml` from source text | Not required (reads `build/spec-cache/`) | **Required** (gitignored) | Initial drafting only |
| `g1_docgen.py` | `tests/coverage.yaml` → `docs/04` | **Not required** | **Not required** | ✅ Identical output from a separate checkout |
| `g1_migration_validate.py` | Compare the English catalog with the fixed Japanese baseline, emit the one-to-one variant-ID map for text-only migrations, and record reviewed semantic replacements separately | **Not required** | **Not required** | ✅ CI build artifacts under `build/` |
| `g1_language_check.py` | Reject Japanese residue in tracked public text, except explicit allowlist entries | **Not required** | **Not required** | ✅ CI build artifact under `build/` |
| `g1_schema_validate.py` | Enforce the G1 JSON Schemas against canonical catalogs and migration artifacts | **Not required** | **Not required** | ✅ CI build artifact under `build/` |
| `g1_validate.py` | **Independently validates** committed artifacts | Required (`--offline` can use the cache) | **Not required** | ✅ |
| `g2_validate.py` | Independently validates case, control, mutant, baseline, feasibility, and approval artifacts | **Not required** | **Not required** | ✅ CI artifact under `build/` |
| `g2_trusted_verify.py` / `g2_ci_verify.sh` | Verify signed G2 approval using an externally pinned validator and runner | **Not required** | **Not required** | ✅ |

Dependencies: `PyYAML`, `pdfminer.six`, `cryptography`, and `jsonschema` (all exact-version and hash pinned in `requirements.lock`)

```bash
python3 tools/g1_docgen.py            # Regenerate docs/04
python3 tools/g1_docgen.py --check    # Only check that it matches the generated artifact (for CI)
python3 tools/g1_migration_validate.py --require-english-fields
python3 tools/g1_schema_validate.py
python3 tools/g1_language_check.py
python3 tools/g1_validate.py          # Fetch and reconcile source text → build/spec-reconcile-report.json (not tracked by Git)
python3 tools/g1_validate.py --offline
python3 tools/g2_validate.py          # build/g2-report.json; approval may remain pending
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
