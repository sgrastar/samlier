#!/usr/bin/env python3
"""Unit tests for the dependency-free Keycloak browser-flow driver."""

from __future__ import annotations

import importlib.util
import json
import pathlib
import sys
import unittest
from http.cookiejar import Cookie, CookieJar


MODULE_PATH = pathlib.Path(__file__).with_name("smoke.py")
SPEC = importlib.util.spec_from_file_location("keycloak_smoke", MODULE_PATH)
assert SPEC and SPEC.loader
smoke = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = smoke
SPEC.loader.exec_module(smoke)


class FormParsingTest(unittest.TestCase):
    def test_parses_keycloak_login_action_and_fields(self) -> None:
        document = """
        <form action="http://keycloak/login?code=a&amp;execution=b" method="post">
          <input type="hidden" name="credentialId" value="">
          <input name="username"><input type="password" name="password">
          <input type="submit" name="login" value="Sign In">
        </form>
        """
        form = smoke.require_form(document, {"username", "password"})
        self.assertEqual("http://keycloak/login?code=a&execution=b", form.action)
        self.assertEqual({"credentialId": "", "username": "", "password": ""}, form.fields)

    def test_parses_saml_post_without_losing_relay_state(self) -> None:
        document = """
        <form action="http://localhost:8080/p/plan/sp/acs/0" method="post">
          <input type="hidden" name="SAMLResponse" value="PHNhbWw+">
          <input type="hidden" name="RelayState" value="run_123&amp;opaque=yes">
        </form>
        """
        form = smoke.require_form(document, {"SAMLResponse"})
        self.assertEqual("PHNhbWw+", form.fields["SAMLResponse"])
        self.assertEqual("run_123&opaque=yes", form.fields["RelayState"])

    def test_rejects_a_page_without_the_expected_form(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "no form contains required fields"):
            smoke.require_form("<html><body>login failed</body></html>", {"SAMLResponse"})

    def test_form_parser_ignores_submit_controls(self) -> None:
        form = smoke.parse_forms(
            '<form action="/login"><input name="username"><input type="submit" name="login"></form>'
        )[0]
        self.assertEqual({"username": ""}, form.fields)

    def test_keycloak_client_matches_unsigned_authn_request_metadata(self) -> None:
        converted = json.dumps({
            "protocol": "saml",
            "attributes": {"saml.client.signature": "true", "saml.server.signature": "true"},
        }).encode()
        configured = json.loads(smoke.configure_keycloak_client(converted))
        self.assertEqual("false", configured["attributes"]["saml.client.signature"])
        self.assertEqual("true", configured["attributes"]["saml.server.signature"])

    def test_rejects_non_saml_converter_output(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "did not produce a SAML client"):
            smoke.configure_keycloak_client(b'{"protocol":"openid-connect"}')

    def test_preflight_allows_warnings(self) -> None:
        report = smoke.require_preflight_success(json.dumps({
            "checks": [
                {"code": "target_metadata", "status": "PASS", "message": "parsed"},
                {"code": "target_to_suite", "status": "WARNING", "message": "asserted"},
            ]
        }).encode())
        self.assertEqual("WARNING", report["checks"][1]["status"])

    def test_preflight_rejects_any_failure_with_context(self) -> None:
        document = json.dumps({
            "checks": [
                {"code": "target_metadata", "status": "FAIL", "message": "connection refused"},
                {"code": "target_to_suite", "status": "WARNING", "message": "asserted"},
            ]
        }).encode()
        with self.assertRaisesRegex(
            RuntimeError, "Samlier preflight failed: target_metadata: connection refused"
        ):
            smoke.require_preflight_success(document)

    def test_localhost_secure_cookie_matches_browser_local_development_behavior(self) -> None:
        jar = CookieJar()
        cookie = Cookie(
            0, "KC_RESTART", "value", None, False, "localhost.local", False, False,
            "/realms/samlier/", True, True, None, True, None, None, {}, False,
        )
        jar.set_cookie(cookie)
        smoke.permit_localhost_http_cookies(jar, "http://localhost:8180/login")
        self.assertFalse(cookie.secure)

    def test_non_local_secure_cookie_is_not_weakened(self) -> None:
        jar = CookieJar()
        cookie = Cookie(
            0, "session", "value", None, False, "example.com", True, False,
            "/", True, True, None, True, None, None, {}, False,
        )
        jar.set_cookie(cookie)
        smoke.permit_localhost_http_cookies(jar, "http://example.com/login")
        self.assertTrue(cookie.secure)


if __name__ == "__main__":
    unittest.main()
