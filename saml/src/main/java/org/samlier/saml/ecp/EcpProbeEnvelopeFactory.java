package org.samlier.saml.ecp;

import java.io.ByteArrayOutputStream;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.samlier.saml.normal.SecureXml;

/** Creates the SOAP request an enhanced client relays to the IdP after removing all SP header blocks. */
public final class EcpProbeEnvelopeFactory {
    private static final String SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String CHANNEL_BINDING =
            "urn:oasis:names:tc:SAML:protocol:ext:channel-binding";
    private static final String SAML_EC = "urn:ietf:params:xml:ns:samlec";

    public byte[] baseline(byte[] authnRequest) {
        return envelope(authnRequest, null, null);
    }

    public byte[] channelBinding(byte[] authnRequest, String type, String value) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("value must not be blank");
        return envelope(authnRequest, type, value);
    }

    public byte[] samlEcSessionKey(byte[] authnRequest) {
        var envelope = SecureXml.parse(envelope(authnRequest, null, null));
        var header = (org.w3c.dom.Element) envelope.getElementsByTagNameNS(SOAP11, "Header").item(0);
        var sessionKey = envelope.createElementNS(SAML_EC, "samlec:SessionKey");
        sessionKey.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:samlec", SAML_EC);
        sessionKey.setAttributeNS(SOAP11, "S:actor", "http://schemas.xmlsoap.org/soap/actor/next");
        sessionKey.setAttributeNS(SOAP11, "S:mustUnderstand", "1");
        var encryptionType = envelope.createElementNS(SAML_EC, "samlec:EncType");
        encryptionType.setTextContent("17");
        sessionKey.appendChild(encryptionType);
        header.appendChild(sessionKey);
        return serialize(envelope);
    }

    private byte[] envelope(byte[] authnRequest, String type, String value) {
        var request = SecureXml.parse(authnRequest).getDocumentElement();
        var document = SecureXml.newDocument();
        var envelope = document.createElementNS(SOAP11, "S:Envelope");
        envelope.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:S", SOAP11);
        document.appendChild(envelope);
        var header = document.createElementNS(SOAP11, "S:Header");
        envelope.appendChild(header);
        if (type != null) {
            var binding = document.createElementNS(CHANNEL_BINDING, "cb:ChannelBindings");
            binding.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:cb", CHANNEL_BINDING);
            binding.setAttribute("Type", type);
            binding.setAttributeNS(SOAP11, "S:actor", "http://schemas.xmlsoap.org/soap/actor/next");
            binding.setAttributeNS(SOAP11, "S:mustUnderstand", "1");
            binding.setTextContent(value);
            header.appendChild(binding);
        }
        var body = document.createElementNS(SOAP11, "S:Body");
        body.appendChild(document.importNode(request, true));
        envelope.appendChild(body);
        return serialize(document);
    }

    private byte[] serialize(org.w3c.dom.Document document) {
        try {
            var factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            var output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to serialize the ECP probe envelope", error);
        }
    }
}
