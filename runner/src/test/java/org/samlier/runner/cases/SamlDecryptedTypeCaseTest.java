package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

class SamlDecryptedTypeCaseTest {
    @Test
    void acceptsEveryStandardPermittedPlaintextTypeIncludingAssertionInsideEncryptedId() throws Exception {
        var pair = keyPair();
        var oracle = new SamlDecryptedTypeCase((wrapper, key) -> plaintext(wrapper));
        for (var fixture : List.of(
                encrypted("EncryptedAssertion", "Assertion"),
                encrypted("EncryptedAttribute", "Attribute"),
                encrypted("EncryptedID", "NameID"),
                encrypted("EncryptedID", "Assertion"))) {
            var outcome = oracle.evaluate(List.of(message("permitted", fixture)), pair.getPrivate());
            assertEquals(Outcome.SATISFIED, outcome.outcome());
        }
    }

    @Test
    void rejectsAKnownWrongPlaintextElement() throws Exception {
        var pair = keyPair();
        var oracle = new SamlDecryptedTypeCase((wrapper, key) -> plaintext(wrapper));
        var outcome = oracle.evaluate(List.of(message("wrong",
                encrypted("EncryptedAssertion", "NameID"))), pair.getPrivate());

        assertEquals(Outcome.VIOLATED, outcome.outcome());
    }

    @Test
    void missingOrWrongDecryptionKeyIsNotATargetViolation() throws Exception {
        var pair = keyPair();
        var fixture = message("encrypted", encrypted("EncryptedID", "NameID"));
        var oracle = new SamlDecryptedTypeCase((wrapper, key) -> {
            throw new SamlException("fixture decryption failed");
        });

        assertEquals(Outcome.NOT_VERIFIED,
                oracle.evaluate(List.of(fixture), null).outcome());
        assertEquals(Outcome.NOT_VERIFIED,
                oracle.evaluate(List.of(fixture), pair.getPrivate()).outcome());
    }

    @Test
    void anExternalElementThatMayUseADerivedTypeRemainsUnverified() throws Exception {
        var pair = keyPair();
        var oracle = new SamlDecryptedTypeCase((wrapper, key) -> SecureXml.parse("""
                <custom:DerivedAssertion xmlns:custom="urn:example:extension"/>
                """.getBytes(StandardCharsets.UTF_8)).getDocumentElement());

        assertEquals(Outcome.NOT_VERIFIED, oracle.evaluate(
                List.of(message("derived", encrypted("EncryptedAssertion", "ignored"))), pair.getPrivate()).outcome());
    }

    @Test
    void aKnownWrongExplicitSamlTypeIsAViolation() throws Exception {
        var pair = keyPair();
        var oracle = new SamlDecryptedTypeCase((wrapper, key) -> SecureXml.parse("""
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="saml:AttributeType"/>
                """.getBytes(StandardCharsets.UTF_8)).getDocumentElement());

        assertEquals(Outcome.VIOLATED, oracle.evaluate(
                List.of(message("wrong-type", encrypted("EncryptedAssertion", "ignored"))), pair.getPrivate()).outcome());
    }

    @Test
    void noEncryptedWrapperIsVacuouslySatisfiedWithNote() {
        assertEquals(Outcome.SATISFIED_WITH_NOTE, new SamlDecryptedTypeCase().evaluate(List.of(message("plain", """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"/>
                """)), null).outcome());
    }

    private java.security.KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String encrypted(String wrapperName, String plaintextLocalName) {
        return """
                <saml:%s xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" fixture:type="%s"
                  xmlns:fixture="urn:samlier:test"><xenc:EncryptedData xmlns:xenc="http://www.w3.org/2001/04/xmlenc#"/></saml:%s>
                """.formatted(wrapperName, plaintextLocalName, wrapperName);
    }

    private Element plaintext(Element wrapper) {
        var localName = wrapper.getAttributeNS("urn:samlier:test", "type");
        return SecureXml.parse(("<saml:" + localName
                + " xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"/>")
                .getBytes(StandardCharsets.UTF_8)).getDocumentElement();
    }

    private TargetTranscriptMessages.Message message(String ref, String xml) {
        return new TargetTranscriptMessages.Message(ref, xml.getBytes(StandardCharsets.UTF_8));
    }
}
