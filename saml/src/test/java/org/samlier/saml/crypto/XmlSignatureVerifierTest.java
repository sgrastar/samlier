package org.samlier.saml.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.saml.normal.SecureXml;

class XmlSignatureVerifierTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void verifiesOnlyADirectSignatureOverTheSelectedRoot() {
        var credentials = credentials();
        var document = SecureXml.parse("""
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" ID="_response">
                  <samlp:Status/>
                </samlp:Response>
                """.getBytes(StandardCharsets.UTF_8));
        var response = document.getDocumentElement();
        new XmlSigner().sign(response, credentials, (org.w3c.dom.Element) response.getFirstChild().getNextSibling());

        var verifier = new XmlSignatureVerifier();
        assertTrue(verifier.hasValidEnvelopedSignature(response, credentials.certificate()));
        response.setAttribute("changed", "after-signing");
        assertFalse(verifier.hasValidEnvelopedSignature(response, credentials.certificate()));
    }

    @Test
    void rejectsMissingAndNestedOnlySignatures() {
        var credentials = credentials();
        var document = SecureXml.parse("""
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="_response">
                  <saml:Assertion ID="_assertion"/>
                </samlp:Response>
                """.getBytes(StandardCharsets.UTF_8));
        var assertion = (org.w3c.dom.Element) document.getElementsByTagNameNS(
                "urn:oasis:names:tc:SAML:2.0:assertion", "Assertion").item(0);
        new XmlSigner().sign(assertion, credentials, null);
        assertFalse(new XmlSignatureVerifier().hasValidEnvelopedSignature(
                document.getDocumentElement(), credentials.certificate()));
    }

    private PlanCredentials credentials() {
        return new FilePlanKeyStore(directory, Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC))
                .getOrCreate("plan_0123456789ABCDEFGHJKMNPQRS");
    }
}
