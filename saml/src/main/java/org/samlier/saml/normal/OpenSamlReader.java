package org.samlier.saml.normal;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.shibboleth.shared.component.ComponentInitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class OpenSamlReader {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    public ParsedMessage read(byte[] xml) {
        initialize();
        var document = SecureXml.parse(xml);
        try {
            var unmarshaller = XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
                    .getUnmarshaller(document.getDocumentElement());
            if (unmarshaller == null) {
                throw new SamlException("OpenSAML has no unmarshaller for "
                        + document.getDocumentElement().getNodeName());
            }
            var object = unmarshaller.unmarshall(document.getDocumentElement());
            return new ParsedMessage(document, object, summary(document.getDocumentElement()));
        } catch (SamlException e) {
            throw e;
        } catch (Exception e) {
            throw new SamlException("OpenSAML rejected the message", e);
        }
    }

    private static void initialize() {
        if (INITIALIZED.get()) return;
        synchronized (INITIALIZED) {
            if (INITIALIZED.get()) return;
            try {
                InitializationService.initialize();
                INITIALIZED.set(true);
            } catch (Exception e) {
                throw new SamlException("Could not initialize OpenSAML", e);
            }
        }
    }

    private Map<String, Object> summary(Element root) {
        return Map.of(
                "type", root.getLocalName(),
                "id", root.getAttribute("ID"),
                "inResponseTo", root.getAttribute("InResponseTo"),
                "destination", root.getAttribute("Destination"),
                "issuer", firstText(root, "urn:oasis:names:tc:SAML:2.0:assertion", "Issuer"),
                "statusCode", firstAttribute(root, "urn:oasis:names:tc:SAML:2.0:protocol", "StatusCode", "Value"));
    }

    private String firstText(Element root, String namespace, String localName) {
        var nodes = root.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private String firstAttribute(Element root, String namespace, String localName, String attribute) {
        var nodes = root.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? "" : ((Element) nodes.item(0)).getAttribute(attribute);
    }

    public record ParsedMessage(Document document, XMLObject openSamlObject, Map<String, Object> summary) {}
}
