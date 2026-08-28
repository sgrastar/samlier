#!/usr/bin/env python3
"""Validate the English-canonical G1 migration against a fixed Japanese baseline.

Human-readable text and text-derived digests may change. Normative structure may not.
The validator also emits a one-to-one old/new variant ID map as a build artifact.
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


def git_yaml(commit: str, path: str) -> dict[str, Any]:
    raw = subprocess.check_output(
        ["git", "show", f"{commit}:{path}"], cwd=ROOT, text=True
    )
    return yaml.safe_load(raw)


def worktree_yaml(path: str) -> dict[str, Any]:
    return yaml.safe_load((ROOT / path).read_text(encoding="utf-8"))


def obligations(catalog: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {
        obligation["key"]: obligation
        for requirement in catalog["requirements"]
        for obligation in requirement["obligations"]
    }


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
        "level": item.get("level"),
        "roles": item.get("roles"),
        "condition": item.get("condition"),
        "testability": item.get("testability"),
        "configuration_failure_semantics": item.get(
            "configuration_failure_semantics"
        ),
        "level_assignment": item.get("level_assignment"),
        "source_clauses": item.get("source_clauses"),
        "reference_evidence": reference_shape(item.get("reference_evidence")),
        "linked_obligations": linked_shape(item.get("linked_obligations")),
        "required_variant_count": len(item.get("required_variants", [])),
        "control_count": len(item.get("controls", [])),
        "open_question": open_question_state(item),
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


def english_field_errors(catalog: dict[str, Any], predicates: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    forbidden_suffixes = ("_ja",)
    for key, item in obligations(catalog).items():
        for field in item:
            if field.endswith(forbidden_suffixes):
                errors.append(f"{key}: forbidden field {field}")
        if "summary_en" not in item:
            errors.append(f"{key}: summary_en is missing")
        for index, variant in enumerate(item.get("required_variants", []), start=1):
            if "description_en" not in variant:
                errors.append(f"{key} variant {index}: description_en is missing")
            for field in variant:
                if field.endswith(forbidden_suffixes):
                    errors.append(f"{key} variant {index}: forbidden field {field}")
    for key, item in predicates.get("predicates", {}).items():
        for field in item:
            if field.endswith(forbidden_suffixes):
                errors.append(f"predicate {key}: forbidden field {field}")
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

    old_obligations = obligations(old_catalog)
    new_obligations = obligations(new_catalog)
    errors: list[str] = []

    if set(old_obligations) != set(new_obligations):
        errors.append(
            "obligation set differs: "
            f"added={sorted(set(new_obligations) - set(old_obligations))} "
            f"removed={sorted(set(old_obligations) - set(new_obligations))}"
        )

    changed: dict[str, Any] = {}
    variant_rows: list[dict[str, Any]] = []
    for key in sorted(set(old_obligations) & set(new_obligations)):
        before = invariant_shape(old_obligations[key])
        after = invariant_shape(new_obligations[key])
        if before != after:
            changed[key] = {"baseline": before, "current": after}
        old_variants = old_obligations[key].get("required_variants", [])
        new_variants = new_obligations[key].get("required_variants", [])
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

    if changed:
        errors.append(f"normative obligation structure changed: {sorted(changed)}")

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

    old_ids = [row["old_id"] for row in variant_rows]
    new_ids = [row["new_id"] for row in variant_rows]
    if len(old_ids) != len(set(old_ids)):
        errors.append("baseline variant IDs are not unique")
    if len(new_ids) != len(set(new_ids)):
        errors.append("current variant IDs are not unique")
    expected_variants = sum(
        len(item.get("required_variants", [])) for item in old_obligations.values()
    )
    if len(variant_rows) != expected_variants:
        errors.append(
            f"variant map is incomplete: {len(variant_rows)} != {expected_variants}"
        )

    english_errors = (
        english_field_errors(new_catalog, new_predicates)
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
                "count": len(variant_rows),
                "mappings": variant_rows,
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
        "variants": len(variant_rows),
        "specs": len(new_specs["specs"]),
        "predicates": len(new_predicates["predicates"]),
        "normative_changes": changed,
        "english_field_errors": english_errors,
        "errors": errors,
        "passed": not errors,
    }
    REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
