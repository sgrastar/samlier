#!/usr/bin/env python3
"""Fail-closed release gate for the signed catalogs, implementation, and generated artifacts."""

from __future__ import annotations

import datetime
import json
import os
import pathlib
import re
import subprocess
import sys
from typing import Any


ROOT = pathlib.Path(__file__).resolve().parents[1]
BUILD = ROOT / "build"
REPORT = BUILD / "release-check-report.json"
EXACT_SHA = re.compile(r"^[0-9a-f]{40}$")


def git(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(ROOT), *args], capture_output=True, text=True, check=False
    )


def exact_commit(value: str | None) -> bool:
    if not value or not EXACT_SHA.fullmatch(value):
        return False
    resolved = git("rev-parse", "--verify", "--quiet", value + "^{commit}")
    return resolved.returncode == 0 and resolved.stdout.strip() == value


def run_check(name: str, command: list[str], environment: dict[str, str]) -> dict[str, Any]:
    print(f"[release-check] {name}", flush=True)
    process = subprocess.run(
        command,
        cwd=ROOT,
        env=environment,
        capture_output=True,
        text=True,
        check=False,
    )
    if process.stdout:
        print(process.stdout, end="" if process.stdout.endswith("\n") else "\n")
    if process.stderr:
        print(process.stderr, file=sys.stderr, end="" if process.stderr.endswith("\n") else "\n")
    return {
        "name": name,
        "result": "PASS" if process.returncode == 0 else "FAIL",
        "exit_code": process.returncode,
        "command": command,
    }


def report_assertions(g1_pin: str, g2_pin: str) -> list[str]:
    errors: list[str] = []
    try:
        g1 = json.loads((BUILD / "spec-reconcile-report.json").read_text(encoding="utf-8"))
        state = g1["g1"]
        provenance = g1["provenance"]
        if state.get("complete") is not True:
            errors.append("g1.complete is not true")
        if provenance.get("validator_source_kind") != "external-pin":
            errors.append("G1 validator source is not an external pin")
        if provenance.get("validator_source") != g1_pin or provenance.get("runner_source") != g1_pin:
            errors.append("G1 validator or runner source differs from G1_TOOLS_COMMIT")
    except Exception as error:  # report absence and malformed reports are release blockers
        errors.append(f"G1 report cannot be verified: {error}")

    try:
        g2 = json.loads((BUILD / "g2-report.json").read_text(encoding="utf-8"))
        state = g2["g2"]
        provenance = g2["provenance"]
        if state.get("complete") is not True:
            errors.append("g2.complete is not true")
        if provenance.get("validator_source_kind") != "external-pin":
            errors.append("G2 validator source is not an external pin")
        if provenance.get("validator_source") != g2_pin or provenance.get("runner_source") != g2_pin:
            errors.append("G2 validator or runner source differs from G2_TOOLS_COMMIT")
    except Exception as error:
        errors.append(f"G2 report cannot be verified: {error}")
    return errors


def main() -> int:
    environment = dict(os.environ)
    environment.pop("PYTHONPATH", None)
    environment["PY"] = sys.executable
    g1_pin = environment.get("G1_TOOLS_COMMIT")
    g2_pin = environment.get("G2_TOOLS_COMMIT")
    pin_errors = []
    if not exact_commit(g1_pin):
        pin_errors.append("G1_TOOLS_COMMIT must be an exact, resolvable 40-character commit SHA")
    if not exact_commit(g2_pin):
        pin_errors.append("G2_TOOLS_COMMIT must be an exact, resolvable 40-character commit SHA")

    checks: list[dict[str, Any]] = []
    if not pin_errors:
        checks.extend(
            [
                run_check("generated G1 documentation", [sys.executable, "tools/g1_docgen.py", "--check"], environment),
                run_check(
                    "English-canonical migration",
                    [sys.executable, "tools/g1_migration_validate.py", "--require-english-fields"],
                    environment,
                ),
                run_check("G1 schemas", [sys.executable, "tools/g1_schema_validate.py"], environment),
                run_check("public-language policy", [sys.executable, "tools/g1_language_check.py"], environment),
                run_check("signed G1 approval and source reconciliation", ["bash", "tools/g1_ci_verify.sh"], environment),
                run_check("signed G2 approval", ["bash", "tools/g2_ci_verify.sh"], environment),
            ]
        )

    assertion_errors = pin_errors or report_assertions(g1_pin or "", g2_pin or "")
    BUILD.mkdir(exist_ok=True)
    report = {
        "task": "releaseCheck",
        "executed_at": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat(),
        "pins": {"g1_tools_commit": g1_pin, "g2_tools_commit": g2_pin},
        "checks": checks,
        "assertion_errors": assertion_errors,
        "complete": not assertion_errors and all(item["result"] == "PASS" for item in checks),
    }
    REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if assertion_errors:
        for error in assertion_errors:
            print("[release-check] BLOCK " + error, file=sys.stderr)
    print(f"[release-check] complete={str(report['complete']).lower()}")
    return 0 if report["complete"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
