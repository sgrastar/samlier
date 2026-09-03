package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class SamlVersionEmissionCaseTest {
    @Test
    void assertionsMustUseTheSupportedIipVersion() {
        assertOutcome(SamlVersionEmissionCase.Rule.ASSERTIONS_SUPPORTED, Outcome.SATISFIED,
                assertion("urn:oasis:names:tc:SAML:2.0:assertion", "2.0"));
        assertOutcome(SamlVersionEmissionCase.Rule.ASSERTIONS_SUPPORTED, Outcome.VIOLATED,
                assertion("urn:oasis:names:tc:SAML:2.0:assertion", "2.1"));
    }

    @Test
    void v2ResponseCannotContainV1OrV1VersionedAssertions() {
        assertOutcome(SamlVersionEmissionCase.Rule.NO_V1_ASSERTION_IN_V2_RESPONSE, Outcome.SATISFIED,
                response(assertion("urn:oasis:names:tc:SAML:2.0:assertion", "2.0")));
        assertOutcome(SamlVersionEmissionCase.Rule.NO_V1_ASSERTION_IN_V2_RESPONSE, Outcome.VIOLATED,
                response(assertion("urn:oasis:names:tc:SAML:1.0:assertion", "1.1")));
        assertOutcome(SamlVersionEmissionCase.Rule.NO_V1_ASSERTION_IN_V2_RESPONSE, Outcome.VIOLATED,
                response(assertion("urn:oasis:names:tc:SAML:2.0:assertion", "1.1")));
    }

    @Test
    void iipAuthnRequestsUseVersionTwo() {
        assertOutcome(SamlVersionEmissionCase.Rule.AUTHN_REQUEST_HIGHEST, Outcome.SATISFIED, request("2.0"));
        assertOutcome(SamlVersionEmissionCase.Rule.AUTHN_REQUEST_HIGHEST, Outcome.VIOLATED, request("1.1"));
    }

    private String assertion(String namespace, String version) {
        return "<saml:Assertion xmlns:saml=\"" + namespace + "\" Version=\"" + version + "\"/>";
    }

    private String response(String assertion) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" Version="2.0">%s</samlp:Response>
                """.formatted(assertion);
    }

    private String request(String version) {
        return """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" Version="%s"/>
                """.formatted(version);
    }

    private void assertOutcome(SamlVersionEmissionCase.Rule rule, Outcome expected, String xml) {
        var outcome = new SamlVersionEmissionCase(rule).evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome(), rule.name());
    }
}
