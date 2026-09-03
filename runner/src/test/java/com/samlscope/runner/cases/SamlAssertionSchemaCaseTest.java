package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

class SamlAssertionSchemaCaseTest {
    @Test
    void validatesPlaintextAssertionsAndRequiredAuthnStatementContent() throws Exception {
        var rule = new SamlAssertionSchemaCase();

        assertEquals(Outcome.SATISFIED,
                rule.evaluate(List.of(message("valid", response(validAssertion()))), null).outcome());
        assertEquals(Outcome.VIOLATED,
                rule.evaluate(List.of(message("invalid", response(invalidAssertion()))), null).outcome());
    }

    @Test
    void validatesAnAssertionRecoveredFromEncryptedAssertion() throws Exception {
        var pair = keyPair();
        var rule = new SamlAssertionSchemaCase((wrapper, key) -> element(validAssertion()));

        var outcome = rule.evaluate(List.of(message("encrypted", encryptedAssertion())), pair.getPrivate());

        assertEquals(Outcome.SATISFIED, outcome.outcome());
    }

    @Test
    void failedOrUnavailableDecryptionIsNotMisreportedAsTargetNonconformance() throws Exception {
        var pair = keyPair();
        var fixture = List.of(message("encrypted", encryptedAssertion()));
        var failed = new SamlAssertionSchemaCase((wrapper, key) -> { throw new SamlException("failed"); });

        assertEquals(Outcome.NOT_VERIFIED, failed.evaluate(fixture, null).outcome());
        assertEquals(Outcome.NOT_VERIFIED, failed.evaluate(fixture, pair.getPrivate()).outcome());
    }

    @Test
    void anErrorResponseWithoutAssertionsIsVacuouslySatisfiedWithNote() {
        var outcome = new SamlAssertionSchemaCase().evaluate(List.of(message("error", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"/>
                """)), null);

        assertEquals(Outcome.SATISFIED_WITH_NOTE, outcome.outcome());
    }

    private String response(String assertion) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">%s</samlp:Response>
                """.formatted(assertion);
    }

    private String validAssertion() {
        return """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  ID="_assertion" Version="2.0" IssueInstant="2026-08-29T00:00:00Z">
                  <saml:Issuer>https://idp.example</saml:Issuer>
                  <saml:AuthnStatement AuthnInstant="2026-08-29T00:00:00Z">
                    <saml:AuthnContext><saml:AuthnContextClassRef>urn:example:loa</saml:AuthnContextClassRef></saml:AuthnContext>
                  </saml:AuthnStatement>
                </saml:Assertion>
                """;
    }

    private String invalidAssertion() {
        return """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  ID="_assertion" Version="2.0" IssueInstant="2026-08-29T00:00:00+00:00">
                  <saml:Issuer>https://idp.example</saml:Issuer>
                  <saml:AuthnStatement AuthnInstant="2026-08-29T00:00:00Z"/>
                </saml:Assertion>
                """;
    }

    private String encryptedAssertion() {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:EncryptedAssertion><xenc:EncryptedData xmlns:xenc="http://www.w3.org/2001/04/xmlenc#"/></saml:EncryptedAssertion>
                </samlp:Response>
                """;
    }

    private Element element(String xml) {
        return SecureXml.parse(xml.getBytes(StandardCharsets.UTF_8)).getDocumentElement();
    }

    private java.security.KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private TargetTranscriptMessages.Message message(String ref, String xml) {
        return new TargetTranscriptMessages.Message(ref, xml.getBytes(StandardCharsets.UTF_8));
    }
}
