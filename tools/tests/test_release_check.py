#!/usr/bin/env python3
"""Unit tests for the fail-closed release checker."""

from __future__ import annotations

import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "release_check.py"
SPEC = importlib.util.spec_from_file_location("release_check", MODULE_PATH)
assert SPEC and SPEC.loader
release_check = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release_check)


class ExactCommitTest(unittest.TestCase):
    def test_rejects_mutable_and_abbreviated_refs_without_running_git(self) -> None:
        with mock.patch.object(release_check, "git") as git:
            for value in (None, "", "HEAD", "main", "a" * 39, "a" * 41, "A" * 40):
                with self.subTest(value=value):
                    self.assertFalse(release_check.exact_commit(value))
            git.assert_not_called()

    def test_accepts_only_an_exactly_resolved_commit(self) -> None:
        sha = "a" * 40
        completed = mock.Mock(returncode=0, stdout=sha + "\n")
        with mock.patch.object(release_check, "git", return_value=completed) as git:
            self.assertTrue(release_check.exact_commit(sha))
            git.assert_called_once_with("rev-parse", "--verify", "--quiet", sha + "^{commit}")

    def test_rejects_a_resolved_value_that_differs_from_the_pin(self) -> None:
        completed = mock.Mock(returncode=0, stdout="b" * 40 + "\n")
        with mock.patch.object(release_check, "git", return_value=completed):
            self.assertFalse(release_check.exact_commit("a" * 40))


class ReportAssertionsTest(unittest.TestCase):
    G1_PIN = "1" * 40
    G2_PIN = "2" * 40

    def write_report(self, root: pathlib.Path, name: str, body: dict) -> None:
        (root / name).write_text(json.dumps(body), encoding="utf-8")

    def valid_g1(self) -> dict:
        return {
            "g1": {"complete": True},
            "provenance": {
                "validator_source_kind": "external-pin",
                "validator_source": self.G1_PIN,
                "runner_source": self.G1_PIN,
            },
        }

    def valid_g2(self) -> dict:
        return {
            "g2": {"complete": True},
            "provenance": {
                "validator_source_kind": "external-pin",
                "validator_source": self.G2_PIN,
                "runner_source": self.G2_PIN,
            },
        }

    def test_accepts_complete_reports_bound_to_both_external_pins(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            self.write_report(root, "spec-reconcile-report.json", self.valid_g1())
            self.write_report(root, "g2-report.json", self.valid_g2())
            with mock.patch.object(release_check, "BUILD", root):
                self.assertEqual([], release_check.report_assertions(self.G1_PIN, self.G2_PIN))

    def test_rejects_missing_reports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with mock.patch.object(release_check, "BUILD", pathlib.Path(directory)):
                errors = release_check.report_assertions(self.G1_PIN, self.G2_PIN)
        self.assertTrue(any("G1 report cannot be verified" in error for error in errors))
        self.assertTrue(any("G2 report cannot be verified" in error for error in errors))

    def test_rejects_incomplete_or_unpinned_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            g1 = self.valid_g1()
            g1["g1"]["complete"] = False
            g1["provenance"]["validator_source_kind"] = "working-tree"
            g2 = self.valid_g2()
            g2["provenance"]["runner_source"] = "3" * 40
            self.write_report(root, "spec-reconcile-report.json", g1)
            self.write_report(root, "g2-report.json", g2)
            with mock.patch.object(release_check, "BUILD", root):
                errors = release_check.report_assertions(self.G1_PIN, self.G2_PIN)
        self.assertIn("g1.complete is not true", errors)
        self.assertIn("G1 validator source is not an external pin", errors)
        self.assertIn("G2 validator or runner source differs from G2_TOOLS_COMMIT", errors)


if __name__ == "__main__":
    unittest.main()
