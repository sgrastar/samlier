#!/usr/bin/env python3
"""Validate the English-canonical G1 migration against a fixed Japanese baseline.

Human-readable text and text-derived digests may change. Normative structure may not,
except for departures recorded by the semantic-exception manifest and covered by G1b.
The validator emits a one-to-one old/new variant ID map for text-only migrations and
records reviewed semantic replacements separately.
"""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path
from typing import Any

import yaml


DEFAULT_BASELINE = "ca54c4b83ac1a3208591f03772b4cf52c62045d4"
ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "g1-migration-report.json"
VARIANT_MAP = ROOT / "build" / "g1-variant-id-map.json"
SEMANTIC_EXCEPTIONS = ROOT / "tools" / "g1-semantic-exceptions.yaml"


def git_yaml(commit: str, path: str) -> dict[str, Any]:
    raw = subprocess.check_output(
        ["git", "show", f"{commit}:{path}"], cwd=ROOT, text=True
    )
    return yaml.safe_load(raw)


def worktree_yaml(path: str) -> dict[str, Any]:
    return yaml.safe_load((ROOT / path).read_text(encoding="utf-8"))


def semantic_exceptions(baseline: str) -> tuple[dict[str, set[str]], list[str], dict[str, Any]]:
    """Load explicitly reviewed departures from the fixed migration baseline."""
    manifest = yaml.safe_load(SEMANTIC_EXCEPTIONS.read_text(encoding="utf-8")) or {}
    errors: list[str] = []
    if manifest.get("baseline_commit") != baseline:
        errors.append(
            "semantic-exception baseline does not match the comparison baseline: "
            f"{manifest.get('baseline_commit')!r} != {baseline!r}"
        )
    if manifest.get("status") != "REQUIRES_G1B_REVIEW":
        errors.append("semantic exceptions must remain REQUIRES_G1B_REVIEW before G1b")
    allowed: dict[str, set[str]] = {}
    seen_ids: set[str] = set()
    for item in manifest.get("exceptions", []):
        exception_id = item.get("id")
        obligation = item.get("obligation")
        fields = set(item.get("fields") or [])
        if exception_id in seen_ids:
            errors.append(f"duplicate semantic-exception id: {exception_id}")
        seen_ids.add(exception_id)
        if obligation in allowed:
            errors.append(f"duplicate semantic-exception obligation: {obligation}")
        allowed[obligation] = fields
    return allowed, errors, manifest


def obligations(catalog: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {
        obligation["key"]: obligation
        for requirement in catalog["requirements"]
        for obligation in requirement["obligations"]
    }


def requirements(catalog: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {requirement["id"]: requirement for requirement in catalog["requirements"]}


def reference_shape(items: list[dict[str, Any]] | None) -> list[dict[str, Any]]:
    return [
        {
            "spec": item.get("spec"),
            "locator": item.get("locator"),
            "section_digest": item.get("section_digest"),
        }
        for item in (items or [])
    ]


def linked_shape(items: list[dict[str, Any]] | None) -> list[dict[str, Any]]:
    """Return only executable link semantics, excluding translated notes."""
    return [
        {
            "obligation": item.get("obligation"),
            "kind": item.get("kind"),
            "variants": item.get("variants"),
            "variant_applicability": item.get(
                "variant_applicability", "owner_condition"
            ),
        }
        for item in (items or [])
    ]


def open_question_state(item: dict[str, Any]) -> bool:
    return any(
        bool(value)
        for key, value in item.items()
        if key.startswith("open_question_")
    )


def invariant_shape(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "authored_by": item.get("authored_by"),
        "level": item.get("level"),
        "roles": item.get("roles"),
        "condition": item.get("condition"),
        "testability": item.get("testability"),
        "configuration_failure_semantics": item.get(
            "configuration_failure_semantics"
        ),
        "level_assignment": item.get("level_assignment"),
        "reference_derivation": item.get("reference_derivation"),
        "references_spec": item.get("references_spec"),
        "source_clauses": item.get("source_clauses"),
        "reference_evidence": reference_shape(item.get("reference_evidence")),
        "linked_obligations": linked_shape(item.get("linked_obligations")),
        "review": {
            key: value
            for key, value in (item.get("review") or {}).items()
            if key != "obligation_digest"
        },
        "required_variant_count": len(item.get("required_variants", [])),
        "control_count": len(item.get("controls", [])),
        "open_question": open_question_state(item),
    }


def requirement_shape(item: dict[str, Any]) -> dict[str, Any]:
    """Return non-translatable requirement metadata."""
    return {
        key: value
        for key, value in item.items()
        if key not in {"obligations", "section_name"}
    }


def catalog_shape(item: dict[str, Any]) -> dict[str, Any]:
    """Return non-translatable catalog metadata."""
    return {
        key: value
        for key, value in item.items()
        if key not in {"requirements", "schema_version", "generated_at", "catalog_digest"}
    }


def predicate_logic(item: dict[str, Any]) -> dict[str, Any]:
    return {
        key: value
        for key, value in item.items()
        if key
        not in {
            "description_ja",
            "description_en",
            "rationale_ja",
            "rationale_en",
        }
    }


def forbidden_japanese_fields(value: Any, path: str = "$") -> list[str]:
    errors: list[str] = []
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key.endswith("_ja"):
                errors.append(f"{child_path}: forbidden Japanese field")
            errors.extend(forbidden_japanese_fields(child, child_path))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            errors.extend(forbidden_japanese_fields(child, f"{path}[{index}]"))
    return errors


def english_field_errors(
    catalog: dict[str, Any], specs: dict[str, Any], predicates: dict[str, Any]
) -> list[str]:
    errors: list[str] = []
    errors.extend(forbidden_japanese_fields(catalog, "coverage"))
    errors.extend(forbidden_japanese_fields(specs, "specs"))
    errors.extend(forbidden_japanese_fields(predicates, "predicates"))
    for key, item in obligations(catalog).items():
        if "summary_en" not in item:
            errors.append(f"{key}: summary_en is missing")
        for index, variant in enumerate(item.get("required_variants", []), start=1):
            if "description_en" not in variant:
                errors.append(f"{key} variant {index}: description_en is missing")
    for key, item in predicates.get("predicates", {}).items():
        for required in ("description_en", "rationale_en"):
            if required not in item:
                errors.append(f"predicate {key}: {required} is missing")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", default=DEFAULT_BASELINE)
    parser.add_argument("--require-english-fields", action="store_true")
    args = parser.parse_args()

    old_catalog = git_yaml(args.baseline, "tests/coverage.yaml")
    new_catalog = worktree_yaml("tests/coverage.yaml")
    old_specs = git_yaml(args.baseline, "tests/specs.yaml")
    new_specs = worktree_yaml("tests/specs.yaml")
    old_predicates = git_yaml(args.baseline, "tests/predicates.yaml")
    new_predicates = worktree_yaml("tests/predicates.yaml")
    allowed_changes, exception_errors, exception_manifest = semantic_exceptions(
        args.baseline
    )

    old_obligations = obligations(old_catalog)
    new_obligations = obligations(new_catalog)
    old_requirements = requirements(old_catalog)
    new_requirements = requirements(new_catalog)
    errors: list[str] = list(exception_errors)

    if set(old_requirements) != set(new_requirements):
        errors.append(
            "requirement set differs: "
            f"added={sorted(set(new_requirements) - set(old_requirements))} "
            f"removed={sorted(set(old_requirements) - set(new_requirements))}"
        )
    changed_requirements = {
        key: {
            "baseline": requirement_shape(old_requirements[key]),
            "current": requirement_shape(new_requirements[key]),
        }
        for key in sorted(set(old_requirements) & set(new_requirements))
        if requirement_shape(old_requirements[key])
        != requirement_shape(new_requirements[key])
    }
    if changed_requirements:
        errors.append(
            f"non-text requirement metadata changed: {sorted(changed_requirements)}"
        )

    changed_catalog = (
        {"baseline": catalog_shape(old_catalog), "current": catalog_shape(new_catalog)}
        if catalog_shape(old_catalog) != catalog_shape(new_catalog)
        else {}
    )
    if changed_catalog:
        errors.append("non-text catalog metadata changed")

    if set(old_obligations) != set(new_obligations):
        errors.append(
            "obligation set differs: "
            f"added={sorted(set(new_obligations) - set(old_obligations))} "
            f"removed={sorted(set(old_obligations) - set(new_obligations))}"
        )

    changed: dict[str, Any] = {}
    variant_rows: list[dict[str, Any]] = []
    reviewed_replacements: list[dict[str, Any]] = []
    replacement_exceptions = {
        item["obligation"]: item["id"]
        for item in exception_manifest.get("exceptions", [])
        if "required_variant_count" in set(item.get("fields") or [])
    }
    for key in sorted(set(old_obligations) & set(new_obligations)):
        before = invariant_shape(old_obligations[key])
        after = invariant_shape(new_obligations[key])
        if before != after:
            changed[key] = {"baseline": before, "current": after}
        old_variants = old_obligations[key].get("required_variants", [])
        new_variants = new_obligations[key].get("required_variants", [])
        if key in replacement_exceptions:
            reviewed_replacements.append(
                {
                    "exception_id": replacement_exceptions[key],
                    "obligation": key,
                    "old_ids": [variant["id"] for variant in old_variants],
                    "new_ids": [variant["id"] for variant in new_variants],
                }
            )
            continue
        for index, (old_variant, new_variant) in enumerate(
            zip(old_variants, new_variants, strict=False), start=1
        ):
            variant_rows.append(
                {
                    "obligation": key,
                    "ordinal": index,
                    "old_id": old_variant["id"],
                    "new_id": new_variant["id"],
                    "old_description": old_variant.get(
                        "description_ja", old_variant.get("description_en")
                    ),
                    "new_description": new_variant.get(
                        "description_en", new_variant.get("description_ja")
                    ),
                }
            )

    actual_changed_fields: dict[str, set[str]] = {
        key: {
            field
            for field in set(value["baseline"]) | set(value["current"])
            if value["baseline"].get(field) != value["current"].get(field)
        }
        for key, value in changed.items()
    }
    unapproved_changes: dict[str, list[str]] = {}
    for key, fields in actual_changed_fields.items():
        unexpected = fields - allowed_changes.get(key, set())
        if unexpected:
            unapproved_changes[key] = sorted(unexpected)
    stale_exceptions: dict[str, list[str]] = {}
    for key, fields in allowed_changes.items():
        missing = fields - actual_changed_fields.get(key, set())
        if missing:
            stale_exceptions[key] = sorted(missing)
    unknown_exception_obligations = sorted(set(allowed_changes) - set(new_obligations))
    if unapproved_changes:
        errors.append(f"unapproved normative changes: {unapproved_changes}")
    if stale_exceptions:
        errors.append(f"stale semantic exceptions: {stale_exceptions}")
    if unknown_exception_obligations:
        errors.append(
            f"semantic exceptions reference unknown obligations: {unknown_exception_obligations}"
        )

    old_spec_logic = {
        key: {k: v for k, v in value.items() if k not in {"title", "note", "url_note"}}
        for key, value in old_specs["specs"].items()
    }
    new_spec_logic = {
        key: {k: v for k, v in value.items() if k not in {"title", "note", "url_note"}}
        for key, value in new_specs["specs"].items()
    }
    if old_spec_logic != new_spec_logic:
        errors.append("spec catalog non-text fields changed")

    old_predicate_logic = {
        key: predicate_logic(value)
        for key, value in old_predicates["predicates"].items()
    }
    new_predicate_logic = {
        key: predicate_logic(value)
        for key, value in new_predicates["predicates"].items()
    }
    if old_predicate_logic != new_predicate_logic:
        errors.append("predicate logic changed")

    all_old_ids = [
        variant["id"]
        for item in old_obligations.values()
        for variant in item.get("required_variants", [])
    ]
    all_new_ids = [
        variant["id"]
        for item in new_obligations.values()
        for variant in item.get("required_variants", [])
    ]
    mapped_old_ids = [row["old_id"] for row in variant_rows]
    mapped_new_ids = [row["new_id"] for row in variant_rows]
    if len(all_old_ids) != len(set(all_old_ids)):
        errors.append("baseline variant IDs are not unique")
    if len(all_new_ids) != len(set(all_new_ids)):
        errors.append("current variant IDs are not unique")
    replacement_keys = set(replacement_exceptions)
    expected_old_mappings = sum(
        len(item.get("required_variants", []))
        for key, item in old_obligations.items()
        if key not in replacement_keys
    )
    expected_new_mappings = sum(
        len(item.get("required_variants", []))
        for key, item in new_obligations.items()
        if key not in replacement_keys
    )
    if expected_old_mappings != expected_new_mappings:
        errors.append(
            "unreviewed variant-count difference remains outside semantic "
            f"replacements: {expected_old_mappings} != {expected_new_mappings}"
        )
    if len(variant_rows) != expected_old_mappings:
        errors.append(
            "text-only variant map is incomplete: "
            f"{len(variant_rows)} != {expected_old_mappings}"
        )
    one_to_one = (
        expected_old_mappings == expected_new_mappings == len(variant_rows)
        and len(mapped_old_ids) == len(set(mapped_old_ids))
        and len(mapped_new_ids) == len(set(mapped_new_ids))
    )

    english_errors = (
        english_field_errors(new_catalog, new_specs, new_predicates)
        if args.require_english_fields
        else []
    )
    errors.extend(english_errors)

    VARIANT_MAP.parent.mkdir(parents=True, exist_ok=True)
    VARIANT_MAP.write_text(
        json.dumps(
            {
                "baseline_commit": args.baseline,
                "current_commit": subprocess.check_output(
                    ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
                ).strip(),
                "scope": "text-only variants excluding reviewed semantic replacements",
                "count": len(variant_rows),
                "baseline_count": len(all_old_ids),
                "current_count": len(all_new_ids),
                "one_to_one": one_to_one,
                "mappings": variant_rows,
                "reviewed_replacements": reviewed_replacements,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    report = {
        "baseline_commit": args.baseline,
        "requirements": len(new_catalog["requirements"]),
        "obligations": len(new_obligations),
        "variants": len(all_new_ids),
        "variant_mappings": len(variant_rows),
        "reviewed_variant_replacements": reviewed_replacements,
        "specs": len(new_specs["specs"]),
        "predicates": len(new_predicates["predicates"]),
        "requirement_changes": changed_requirements,
        "catalog_changes": changed_catalog,
        "normative_changes": changed,
        "semantic_exception_status": exception_manifest.get("status"),
        "semantic_exceptions": {
            key: sorted(actual_changed_fields.get(key, set()))
            for key in sorted(allowed_changes)
        },
        "unapproved_normative_changes": unapproved_changes,
        "stale_semantic_exceptions": stale_exceptions,
        "english_field_errors": english_errors,
        "errors": errors,
        "passed": not errors,
    }
    REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
