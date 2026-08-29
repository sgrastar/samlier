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

    public byte[] baseline(byte[] authnRequest) {
        var request = SecureXml.parse(authnRequest).getDocumentElement();
        var document = SecureXml.newDocument();
        var envelope = document.createElementNS(SOAP11, "S:Envelope");
        envelope.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:S", SOAP11);
        document.appendChild(envelope);
        envelope.appendChild(document.createElementNS(SOAP11, "S:Header"));
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
