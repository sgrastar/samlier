package com.samlscope.saml.metadata;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import com.samlscope.saml.normal.SamlException;
import com.samlscope.saml.normal.SecureXml;
import org.w3c.dom.Element;

public final class TargetMetadataParser {
    private static final String MD = "urn:oasis:names:tc:SAML:2.0:metadata";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";

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
                endpoints(entity, "SingleLogoutService"), endpoints(entity, "AssertionConsumerService"),
                signingCertificates(entity));
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

    private java.util.List<X509Certificate> signingCertificates(Element entity) {
        var result = new ArrayList<X509Certificate>();
        var descriptors = entity.getElementsByTagNameNS(MD, "KeyDescriptor");
        try {
            var factory = CertificateFactory.getInstance("X.509");
            for (var descriptorIndex = 0; descriptorIndex < descriptors.getLength(); descriptorIndex++) {
                var descriptor = (Element) descriptors.item(descriptorIndex);
                var use = descriptor.getAttribute("use");
                if (!use.isBlank() && !"signing".equals(use)) continue;
                var certificates = descriptor.getElementsByTagNameNS(DS, "X509Certificate");
                for (var certificateIndex = 0; certificateIndex < certificates.getLength(); certificateIndex++) {
                    var lexical = certificates.item(certificateIndex).getTextContent().replaceAll("\\s+", "");
                    var certificate = (X509Certificate) factory.generateCertificate(
                            new ByteArrayInputStream(Base64.getDecoder().decode(lexical)));
                    if (result.stream().noneMatch(existing -> same(existing, certificate))) result.add(certificate);
                }
            }
            return List.copyOf(result);
        } catch (Exception invalidCertificate) {
            throw new SamlException("Target metadata contains an invalid signing certificate", invalidCertificate);
        }
    }

    private boolean same(X509Certificate left, X509Certificate right) {
        try {
            return java.util.Arrays.equals(left.getEncoded(), right.getEncoded());
        } catch (java.security.cert.CertificateEncodingException impossible) {
            throw new SamlException("Could not compare target signing certificates", impossible);
        }
    }
}
