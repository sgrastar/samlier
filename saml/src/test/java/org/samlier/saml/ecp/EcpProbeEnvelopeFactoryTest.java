package org.samlier.saml.ecp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.samlier.saml.normal.SecureXml;

class EcpProbeEnvelopeFactoryTest {
    @Test
    void wrapsTheRequestAndDoesNotForwardServiceProviderHeaders() {
        var request = "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ID=\"_r\"/>"
                .getBytes(StandardCharsets.UTF_8);
        var envelope = new EcpProbeEnvelopeFactory().baseline(request);
        var document = SecureXml.parse(envelope);

        assertEquals("Envelope", document.getDocumentElement().getLocalName());
        assertEquals(1, document.getElementsByTagNameNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "Header").getLength());
        assertEquals(0, document.getElementsByTagNameNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "Header").item(0).getChildNodes().getLength());
        assertEquals(1, document.getElementsByTagNameNS(
                "urn:oasis:names:tc:SAML:2.0:protocol", "AuthnRequest").getLength());
    }
}
