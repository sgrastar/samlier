package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.evaluation.Outcome;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.saml.crypto.PlanCredentials;
import org.samlier.saml.crypto.XmlSigner;
import org.samlier.saml.normal.SecureXml;

class SamlCbcEncryptedAssertionSignatureCaseTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void cbcEncryptedAssertionRequiresAValidResponseSignature() {
        var credentials = credentials();
        var signed = signedResponse(credentials, "http://www.w3.org/2001/04/xmlenc#aes256-cbc");
        assertOutcome(credentials, Outcome.SATISFIED, signed);
        assertOutcome(credentials, Outcome.VIOLATED, unsignedResponse("http://www.w3.org/2001/04/xmlenc#aes256-cbc"));
        assertOutcome(credentials, Outcome.VIOLATED, signed.replace("ciphertext", "tampered"));
    }

    @Test
    void nonCbcAndNoEncryptedAssertionAreOutsideRuntimeScope() {
        var credentials = credentials();
        assertOutcome(credentials, Outcome.SATISFIED_WITH_NOTE,
                unsignedResponse("http://www.w3.org/2009/xmlenc11#aes256-gcm"));
        assertOutcome(credentials, Outcome.SATISFIED_WITH_NOTE, """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" ID="_response"/>
                """);
    }

    @Test
    void missingTargetVerificationKeyIsNotAFalseViolation() {
        var outcome = new SamlCbcEncryptedAssertionSignatureCase(
                List.of(), new org.samlier.saml.crypto.XmlSignatureVerifier()).evaluate(List.of(
                new TargetTranscriptMessages.Message(
                        "message", unsignedResponse("http://www.w3.org/2001/04/xmlenc#aes256-cbc")
                        .getBytes(StandardCharsets.UTF_8))));

        assertEquals(Outcome.NOT_VERIFIED, outcome.outcome());
    }

    private String signedResponse(PlanCredentials credentials, String algorithm) {
        var document = SecureXml.parse(unsignedResponse(algorithm).getBytes(StandardCharsets.UTF_8));
        var response = document.getDocumentElement();
        new XmlSigner().sign(response, credentials, response.getFirstChild() instanceof org.w3c.dom.Element element ? element : null);
        return new String(SecureXml.serialize(document), StandardCharsets.UTF_8);
    }

    private String unsignedResponse(String algorithm) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" ID="_response">
                  <saml:EncryptedAssertion><xenc:EncryptedData><xenc:EncryptionMethod Algorithm="%s"/>
                    <xenc:CipherData><xenc:CipherValue>ciphertext</xenc:CipherValue></xenc:CipherData>
                  </xenc:EncryptedData></saml:EncryptedAssertion>
                </samlp:Response>
                """.formatted(algorithm);
    }

    private PlanCredentials credentials() {
        return new FilePlanKeyStore(directory, Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC))
                .getOrCreate("plan_0123456789ABCDEFGHJKMNPQRS");
    }

    private void assertOutcome(PlanCredentials credentials, Outcome expected, String xml) {
        var outcome = new SamlCbcEncryptedAssertionSignatureCase(credentials.certificate()).evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome());
    }
}
