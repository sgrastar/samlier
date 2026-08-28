#!/usr/bin/env python3
"""Validate the canonical G1 YAML catalogs and generated migration artifacts."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import yaml
from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "build" / "g1-schema-report.json"
TARGETS = (
    ("tests/coverage.yaml", "schema/g1-coverage-v2.json"),
    ("tests/predicates.yaml", "schema/g1-predicates-v2.json"),
    ("tests/specs.yaml", "schema/g1-specs-v2.json"),
    ("build/g1-variant-id-map.json", "schema/g1-variant-map-v1.json"),
    ("tools/g1-semantic-exceptions.yaml", "schema/g1-semantic-exceptions-v1.json"),
)


def load_data(path: Path) -> Any:
    if path.suffix == ".json":
        return json.loads(path.read_text(encoding="utf-8"))
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def display_path(parts: Any) -> str:
    return "$" + "".join(
        f"[{part}]" if isinstance(part, int) else f".{part}" for part in parts
    )


def legacy_field_errors(value: Any, path: str = "$") -> list[dict[str, str]]:
    """Reject any newly introduced Japanese-language field, at any nesting depth."""
    errors: list[dict[str, str]] = []
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if str(key).endswith("_ja"):
                errors.append(
                    {"path": child_path, "message": "legacy Japanese-language field"}
                )
            errors.extend(legacy_field_errors(child, child_path))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            errors.extend(legacy_field_errors(child, f"{path}[{index}]"))
    return errors


def main() -> int:
    results: list[dict[str, Any]] = []
    all_errors: list[dict[str, str]] = []
    for target_name, schema_name in TARGETS:
        target = ROOT / target_name
        schema_path = ROOT / schema_name
        item_errors: list[dict[str, str]] = []
        try:
            schema = json.loads(schema_path.read_text(encoding="utf-8"))
            Draft202012Validator.check_schema(schema)
            instance = load_data(target)
            validator = Draft202012Validator(schema)
            for error in sorted(
                validator.iter_errors(instance),
                key=lambda item: (display_path(item.absolute_path), item.message),
            ):
                item_errors.append(
                    {
                        "path": display_path(error.absolute_path),
                        "message": error.message,
                    }
                )
            item_errors.extend(legacy_field_errors(instance))
        except (OSError, ValueError, yaml.YAMLError) as error:
            item_errors.append({"path": "$", "message": str(error)})
        results.append(
            {
                "target": target_name,
                "schema": schema_name,
                "passed": not item_errors,
                "errors": item_errors,
            }
        )
        all_errors.extend(
            {"target": target_name, **error} for error in item_errors
        )

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "targets": results,
        "error_count": len(all_errors),
        "errors": all_errors,
        "passed": not all_errors,
    }
    REPORT.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(
        f"G1 schema validation: {len(results) - sum(not r['passed'] for r in results)}"
        f"/{len(results)} targets passed"
    )
    for error in all_errors[:50]:
        print(f"{error['target']}:{error['path']}: {error['message']}")
    return 0 if not all_errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
