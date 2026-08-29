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

    @Test
    void addsTheClientChannelBindingOnlyWhenRequested() {
        var request = "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ID=\"_r\"/>"
                .getBytes(StandardCharsets.UTF_8);
        var document = SecureXml.parse(new EcpProbeEnvelopeFactory().channelBinding(
                request, "tls-server-end-point", "YWJj"));
        var bindings = document.getElementsByTagNameNS(
                "urn:oasis:names:tc:SAML:protocol:ext:channel-binding", "ChannelBindings");
        assertEquals(1, bindings.getLength());
        var binding = (org.w3c.dom.Element) bindings.item(0);
        assertEquals("tls-server-end-point", binding.getAttribute("Type"));
        assertEquals("YWJj", binding.getTextContent());
        assertEquals("http://schemas.xmlsoap.org/soap/actor/next", binding.getAttributeNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "actor"));
        assertEquals("1", binding.getAttributeNS("http://schemas.xmlsoap.org/soap/envelope/", "mustUnderstand"));
    }

    @Test
    void requestsTheMandatorySamlEcEncryptionType() {
        var request = "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ID=\"_r\"/>"
                .getBytes(StandardCharsets.UTF_8);
        var document = SecureXml.parse(new EcpProbeEnvelopeFactory().samlEcSessionKey(request));
        var sessionKey = (org.w3c.dom.Element) document.getElementsByTagNameNS(
                "urn:ietf:params:xml:ns:samlec", "SessionKey").item(0);
        assertEquals("1", sessionKey.getAttributeNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "mustUnderstand"));
        assertEquals("http://schemas.xmlsoap.org/soap/actor/next", sessionKey.getAttributeNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "actor"));
        assertEquals("17", document.getElementsByTagNameNS(
                "urn:ietf:params:xml:ns:samlec", "EncType").item(0).getTextContent());
    }
}
