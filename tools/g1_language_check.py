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

    REPORT.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "allowlist": patterns,
        "files_with_residue": len({item["path"] for item in matches}),
        "matching_lines": len(matches),
        "matching_characters": sum(int(item["characters"]) for item in matches),
        "matches": matches,
        "passed": not matches,
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
    return 0 if not matches or args.report_only else 1


if __name__ == "__main__":
    raise SystemExit(main())
