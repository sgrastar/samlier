package org.samlier.saml.ecp;

import java.io.ByteArrayOutputStream;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

/** Production boundary for constructing the ECP request forwarded to an IdP. */
public final class EcpEnvelopeForwarder {
    private static final String SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/";

    public byte[] removeServiceProviderHeaders(byte[] serviceProviderEnvelope) {
        var document = SecureXml.parse(serviceProviderEnvelope);
        var headers = document.getElementsByTagNameNS(SOAP11, "Header");
        if (headers.getLength() != 1) {
            throw new IllegalArgumentException("A SOAP 1.1 envelope must contain exactly one Header");
        }
        var header = (Element) headers.item(0);
        while (header.hasChildNodes()) {
            header.removeChild(header.getFirstChild());
        }
        try {
            var factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            var output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to serialize the forwarded ECP envelope", error);
        }
    }
}
