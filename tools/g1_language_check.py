#!/usr/bin/env python3
"""Fail when tracked public text contains Japanese characters."""

from __future__ import annotations

import argparse
import fnmatch
import json
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ALLOWLIST = ROOT / "tools" / "g1-language-allowlist.txt"
REPORT = ROOT / "build" / "g1-language-report.json"
JAPANESE = re.compile(
    r"[\u3040-\u30ff\u31f0-\u31ff\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]"
)
LEGACY_FIELD = re.compile(r"\b[A-Za-z0-9_.-]*_ja\b")
# These files necessarily discuss or consume the legacy baseline vocabulary.
# They are not canonical public data formats.
LEGACY_FIELD_EXEMPTIONS = {
    "docs/11-review-log.md",
    "tools/g1_migration_validate.py",
    "tools/g1_language_check.py",
    "tools/g1_schema_validate.py",
    "schema/g1-coverage-v2.json",
    "schema/g1-predicates-v2.json",
    "schema/g1-specs-v2.json",
    "schema/g1-variant-map-v1.json",
    "schema/g1-semantic-exceptions-v1.json",
}


def tracked_files() -> list[str]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [item.decode("utf-8") for item in raw.split(b"\0") if item]


def allow_patterns() -> list[str]:
    if not ALLOWLIST.exists():
        return []
    return [
        line.strip()
        for line in ALLOWLIST.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def is_binary(data: bytes) -> bool:
    return b"\0" in data[:8192]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--report-only",
        action="store_true",
        help="Report residue without failing; intended only for intermediate migration commits.",
    )
    args = parser.parse_args()
    patterns = allow_patterns()
    matches: list[dict[str, object]] = []
    legacy_field_matches: list[dict[str, object]] = []

    for relative in tracked_files():
        if relative.startswith("build/spec-cache/"):
            continue
        if any(fnmatch.fnmatch(relative, pattern) for pattern in patterns):
            continue
        path = ROOT / relative
        if not path.is_file():
            continue
        data = path.read_bytes()
        if is_binary(data):
            continue
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            continue
        for line_number, line in enumerate(text.splitlines(), start=1):
            found = JAPANESE.findall(line)
            if found:
                matches.append(
                    {
                        "path": relative,
                        "line": line_number,
                        "characters": len(found),
                        "text": line.strip(),
                    }
                )
            if relative not in LEGACY_FIELD_EXEMPTIONS:
                legacy = LEGACY_FIELD.findall(line)
                if legacy:
                    legacy_field_matches.append(
                        {
                            "path": relative,
                            "line": line_number,
                            "fields": legacy,
                            "text": line.strip(),
                        }
                    )

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "allowlist": patterns,
        "files_with_residue": len({item["path"] for item in matches}),
        "matching_lines": len(matches),
        "matching_characters": sum(int(item["characters"]) for item in matches),
        "matches": matches,
        "legacy_field_exemptions": sorted(LEGACY_FIELD_EXEMPTIONS),
        "legacy_field_matches": legacy_field_matches,
        "passed": not matches and not legacy_field_matches,
    }
    REPORT.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(
        f"Japanese residue: {payload['files_with_residue']} files, "
        f"{payload['matching_lines']} lines, {payload['matching_characters']} characters"
    )
    for item in matches[:50]:
        print(f"{item['path']}:{item['line']}: {item['text']}")
    if len(matches) > 50:
        print(f"... {len(matches) - 50} more lines; see {REPORT.relative_to(ROOT)}")
    print(f"Legacy Japanese-language fields: {len(legacy_field_matches)} matches")
    for item in legacy_field_matches[:50]:
        print(f"{item['path']}:{item['line']}: {', '.join(item['fields'])}")
    return 0 if (not matches and not legacy_field_matches) or args.report_only else 1


if __name__ == "__main__":
    raise SystemExit(main())
