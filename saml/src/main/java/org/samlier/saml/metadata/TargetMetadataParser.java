package org.samlier.saml.metadata;

import java.net.URI;
import java.util.ArrayList;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.w3c.dom.Element;

public final class TargetMetadataParser {
    private static final String MD = "urn:oasis:names:tc:SAML:2.0:metadata";

    public TargetMetadata parse(byte[] xml, String expectedEntityId) {
        var document = SecureXml.parse(xml);
        Element entity = null;
        var entities = document.getElementsByTagNameNS(MD, "EntityDescriptor");
        for (var i = 0; i < entities.getLength(); i++) {
            var candidate = (Element) entities.item(i);
            if (expectedEntityId.equals(candidate.getAttribute("entityID"))) {
                entity = candidate;
                break;
            }
        }
        if (entity == null && document.getDocumentElement().getLocalName().equals("EntityDescriptor")) {
            entity = document.getDocumentElement();
        }
        if (entity == null) throw new SamlException("Target metadata does not contain EntityDescriptor");
        if (!expectedEntityId.equals(entity.getAttribute("entityID"))) {
            throw new SamlException("Target metadata entityID does not match the Test Plan");
        }
        return new TargetMetadata(entity.getAttribute("entityID"), endpoints(entity, "SingleSignOnService"),
                endpoints(entity, "AssertionConsumerService"));
    }

    private java.util.List<TargetMetadata.Endpoint> endpoints(Element entity, String localName) {
        var result = new ArrayList<TargetMetadata.Endpoint>();
        var nodes = entity.getElementsByTagNameNS(MD, localName);
        for (var i = 0; i < nodes.getLength(); i++) {
            var element = (Element) nodes.item(i);
            var index = element.hasAttribute("index") ? Integer.valueOf(element.getAttribute("index")) : null;
            result.add(new TargetMetadata.Endpoint(
                    element.getAttribute("Binding"),
                    URI.create(element.getAttribute("Location")),
                    index,
                    "true".equals(element.getAttribute("isDefault"))));
        }
        return result;
    }
}
