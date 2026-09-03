package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class SamlEncryptionObservationCaseTest {
    @Test
    void distinctEncryptionOperationsRequireDistinctEncryptedIdCiphertext() {
        var oracle = new SamlEncryptedIdentifierUniquenessCase();
        assertEquals(Outcome.SATISFIED, oracle.evaluate(List.of(
                message("one", response("_one", "cipher-one")),
                message("two", response("_two", "cipher-two")))).outcome());
        assertEquals(Outcome.VIOLATED, oracle.evaluate(List.of(
                message("one", response("_one", "reused")),
                message("two", response("_two", "reused")))).outcome());
    }

    @Test
    void byteIdenticalRetransmissionIsNotInventedAsANewEncryptionOperation() {
        var xml = response("_same", "same-operation");
        var outcome = new SamlEncryptedIdentifierUniquenessCase().evaluate(List.of(
                message("first-delivery", xml), message("retry-delivery", xml)));
        assertEquals(Outcome.SATISFIED, outcome.outcome());
    }

    @Test
    void wrappedKeyCiphertextIsNotConfusedWithEncryptedIdentifierCiphertext() {
        var xml = """
                <saml:EncryptedID xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
                  <xenc:EncryptedData><ds:KeyInfo><xenc:EncryptedKey><xenc:CipherData><xenc:CipherValue>same-wrapped-key</xenc:CipherValue></xenc:CipherData></xenc:EncryptedKey></ds:KeyInfo>
                  <xenc:CipherData><xenc:CipherValue>%s</xenc:CipherValue></xenc:CipherData></xenc:EncryptedData>
                </saml:EncryptedID>
                """;
        var outcome = new SamlEncryptedIdentifierUniquenessCase().evaluate(List.of(
                message("one", xml.formatted("identifier-one")),
                message("two", xml.formatted("identifier-two"))));
        assertEquals(Outcome.SATISFIED, outcome.outcome());
    }

    @Test
    void noEncryptedIdentifierIsAnObservationNote() {
        var outcome = new SamlEncryptedIdentifierUniquenessCase().evaluate(List.of(message("plain", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"/>
                """)));
        assertEquals(Outcome.SATISFIED_WITH_NOTE, outcome.outcome());
    }

    @Test
    void wrappedKeyRecipientMustBePresentAndMatchPeerEntityId() {
        var oracle = new SamlEncryptedKeyRecipientCase("https://suite.example/sp");
        assertOutcome(oracle, Outcome.SATISFIED, encryptedKey("https://suite.example/sp"));
        assertOutcome(oracle, Outcome.VIOLATED, encryptedKey(null));
        assertOutcome(oracle, Outcome.VIOLATED, encryptedKey("https://other.example/sp"));
        assertOutcome(oracle, Outcome.SATISFIED_WITH_NOTE, """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"/>
                """);
    }

    private String response(String id, String cipher) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" ID="%s">
                  <saml:EncryptedID><xenc:EncryptedData><xenc:CipherData><xenc:CipherValue>%s</xenc:CipherValue></xenc:CipherData></xenc:EncryptedData></saml:EncryptedID>
                </samlp:Response>
                """.formatted(id, cipher);
    }

    private String encryptedKey(String recipient) {
        var attribute = recipient == null ? "" : " Recipient=\"" + recipient + "\"";
        return "<xenc:EncryptedKey xmlns:xenc=\"http://www.w3.org/2001/04/xmlenc#\"" + attribute + "/>";
    }

    private TargetTranscriptMessages.Message message(String evidence, String xml) {
        return new TargetTranscriptMessages.Message(evidence, xml.getBytes(StandardCharsets.UTF_8));
    }

    private void assertOutcome(SamlEncryptedKeyRecipientCase oracle, Outcome expected, String xml) {
        assertEquals(expected, oracle.evaluate(List.of(message("message", xml))).outcome());
    }
}
