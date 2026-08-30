#!/usr/bin/env python3
"""Unit tests for the Apple Container Keycloak launcher."""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest


MODULE_PATH = pathlib.Path(__file__).with_name("prepare-smoke-apple.py")
SPEC = importlib.util.spec_from_file_location("prepare_smoke_apple", MODULE_PATH)
assert SPEC and SPEC.loader
launcher = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = launcher
SPEC.loader.exec_module(launcher)


class AppleContainerInspectionTest(unittest.TestCase):
    def test_reads_full_image_descriptor_digest(self) -> None:
        digest = "sha256:" + "a" * 64
        document = [{"configuration": {"descriptor": {"digest": digest}}}]
        self.assertEqual(digest, launcher.image_digest(document))

    def test_rejects_short_or_non_sha256_image_ids(self) -> None:
        for digest in ("abc123", "sha256:abc123", "sha512:" + "a" * 64):
            with self.subTest(digest=digest), self.assertRaisesRegex(RuntimeError, "invalid"):
                launcher.image_digest([
                    {"configuration": {"descriptor": {"digest": digest}}}
                ])

    def test_reads_ipv4_from_the_requested_network_only(self) -> None:
        document = [{"status": {"networks": [
            {"network": "default", "ipv4Address": "192.168.65.2/24"},
            {"network": "samlier-smoke", "ipv4Address": "192.168.64.2/24"},
        ]}}]
        self.assertEqual("192.168.64.2", launcher.network_ipv4(document, "samlier-smoke"))

    def test_samlier_command_binds_actual_digest_and_data_directory(self) -> None:
        digest = "sha256:" + "b" * 64
        result = launcher.samlier_run_command(
            image="samlier:test",
            digest=digest,
            network="samlier-smoke",
            name="samlier-app",
            data_dir=pathlib.Path("/tmp/samlier-data"),
        )
        self.assertIn(f"SAMLIER_IMAGE_DIGEST={digest}", result)
        self.assertIn("/tmp/samlier-data:/data", result)
        self.assertEqual("samlier:test", result[-1])

    def test_keycloak_command_uses_pinned_image_and_imports_read_only(self) -> None:
        result = launcher.keycloak_run_command(network="samlier-smoke", name="keycloak")
        self.assertIn(launcher.KEYCLOAK_IMAGE, result)
        self.assertTrue(any(
            value.endswith(
                "/realm-samlier.json:/opt/keycloak/data/import/realm-samlier.json:ro"
            )
            for value in result
        ))
        self.assertFalse(any(value.endswith(":/opt/keycloak/data/import:ro") for value in result))
        self.assertEqual(["start-dev", "--import-realm"], result[-2:])


class RepositoryLaunchConfigurationTest(unittest.TestCase):
    def test_runtime_image_has_no_implicit_data_volume(self) -> None:
        dockerfile = (launcher.ROOT / "Dockerfile").read_text()
        self.assertNotIn('VOLUME ["/data"]', dockerfile)

    def test_local_runtime_data_is_excluded_from_the_build_context(self) -> None:
        ignored = {
            line.strip()
            for line in (launcher.ROOT / ".dockerignore").read_text().splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        }
        self.assertIn("data", ignored)
        self.assertIn(".data", ignored)

    def test_compose_requires_image_digest(self) -> None:
        compose = (launcher.ROOT / "dev/keycloak/compose.yml").read_text()
        self.assertIn("SAMLIER_IMAGE_DIGEST:", compose)
        self.assertIn("SAMLIER_IMAGE_DIGEST must identify the image being run", compose)

    def test_docker_launcher_derives_and_checks_actual_image_digest(self) -> None:
        script = (launcher.ROOT / "dev/keycloak/prepare-smoke.sh").read_text()
        self.assertIn("docker image inspect --format '{{.Id}}'", script)
        self.assertIn('PROVIDED_IMAGE_DIGEST" != "$ACTUAL_IMAGE_DIGEST', script)

    def test_container_ci_exports_the_built_image_digest_for_compose_cleanup(self) -> None:
        workflow = (launcher.ROOT / ".github/workflows/build.yml").read_text()
        build_step = workflow.split("- name: Build container image", 1)[1].split(
            "- name: Run pinned Keycloak SAML round trip", 1
        )[0]
        self.assertIn("docker image inspect", build_step)
        self.assertIn("SAMLIER_IMAGE_DIGEST=", build_step)
        self.assertIn('>> "$GITHUB_ENV"', build_step)


if __name__ == "__main__":
    unittest.main()
