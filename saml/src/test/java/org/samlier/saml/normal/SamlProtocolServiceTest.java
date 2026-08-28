package org.samlier.saml.normal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.xml.security.signature.XMLSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.saml.SamlTestFixtures;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.saml.crypto.XmlSigner;

class SamlProtocolServiceTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void redirectAuthnRequestRoundTripsWithoutReconstructingTheRawQuery() {
        var plan = SamlTestFixtures.idpPlan();
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var service = new SamlProtocolService(URI.create("https://peer.example"),
                new FilePlanKeyStore(directory, clock), new XmlSigner(), new OpenSamlReader(), clock);
        var request = service.buildAuthnRequest(plan, URI.create("https://idp.example/sso"), "run_0123456789ABCDEFGHJKMNPQRS");
        var decoded = service.decodeRedirect(request.redirect().getRawQuery(), "SAMLRequest");
        assertEquals(request.id(), decoded.parsed().summary().get("id"));
        assertEquals("run_0123456789ABCDEFGHJKMNPQRS", decoded.relayState());
        assertTrue(new String(decoded.xml(), java.nio.charset.StandardCharsets.UTF_8).contains("AuthnRequest"));
    }

    @Test
    void generatedResponseRetainsAValidAssertionSignatureAfterSerialization() throws Exception {
        var plan = SamlTestFixtures.idpPlan();
        var clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
        var keyStore = new FilePlanKeyStore(directory, clock);
        var service = new SamlProtocolService(URI.create("https://peer.example"),
                keyStore, new XmlSigner(), new OpenSamlReader(), clock);
        var request = service.buildAuthnRequest(plan, URI.create("https://idp.example/sso"), "relay");
        var decoded = service.decodeRedirect(request.redirect().getRawQuery(), "SAMLRequest");
        var response = service.buildResponse(plan, decoded, URI.create("https://sp.example/acs"), "subject");
        var document = SecureXml.parse(response.xml());
        var assertion = (org.w3c.dom.Element) document.getElementsByTagNameNS(
                "urn:oasis:names:tc:SAML:2.0:assertion", "Assertion").item(0);
        assertion.setIdAttribute("ID", true);
        var signatureElement = (org.w3c.dom.Element) assertion.getElementsByTagNameNS(
                "http://www.w3.org/2000/09/xmldsig#", "Signature").item(0);
        var signature = new XMLSignature(signatureElement, "");
        assertTrue(signature.checkSignatureValue(keyStore.getOrCreate(plan.id()).certificate()));
    }
}
