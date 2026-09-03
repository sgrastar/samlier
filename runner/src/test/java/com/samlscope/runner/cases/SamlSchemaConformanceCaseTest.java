package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class SamlSchemaConformanceCaseTest {
    @Test
    void authnRequestRequiresIdVersionAndIssueInstant() {
        assertOutcome(SamlSchemaConformanceCase.Rule.AUTHN_REQUEST, Outcome.SATISFIED, """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  ID="_request" Version="2.0" IssueInstant="2026-08-29T00:00:00Z"/>
                """);
        assertOutcome(SamlSchemaConformanceCase.Rule.AUTHN_REQUEST, Outcome.VIOLATED, """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  Version="2.0" IssueInstant="2026-08-29T00:00:00Z"/>
                """);
        assertOutcome(SamlSchemaConformanceCase.Rule.AUTHN_REQUEST, Outcome.VIOLATED, """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  ID="_request" Version="2.1" IssueInstant="2026-08-29T09:00:00+09:00"/>
                """);
    }

    @Test
    void responseRequiresStatus() {
        assertOutcome(SamlSchemaConformanceCase.Rule.RESPONSE, Outcome.SATISFIED, response(
                "<samlp:Status><samlp:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/></samlp:Status>"));
        assertOutcome(SamlSchemaConformanceCase.Rule.RESPONSE, Outcome.VIOLATED, response(""));
        assertOutcome(SamlSchemaConformanceCase.Rule.RESPONSE, Outcome.VIOLATED, """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  ID="_response" Version="2.0" IssueInstant="2026-08-29T09:00:00+09:00">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/></samlp:Status>
                </samlp:Response>
                """);
    }

    private String response(String status) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  ID="_response" Version="2.0" IssueInstant="2026-08-29T00:00:00Z">%s</samlp:Response>
                """.formatted(status);
    }

    private void assertOutcome(SamlSchemaConformanceCase.Rule rule, Outcome expected, String xml) {
        var outcome = new SamlSchemaConformanceCase(rule).evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome(), rule.name());
    }
}
