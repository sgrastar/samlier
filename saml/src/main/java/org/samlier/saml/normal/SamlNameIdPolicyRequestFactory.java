package org.samlier.saml.normal;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import javax.xml.XMLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Builds product-neutral AuthnRequest fixtures for the approved NameIDPolicy matrix. */
public final class SamlNameIdPolicyRequestFactory {
    public static final String PERSISTENT = "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent";
    public static final String TRANSIENT = "urn:oasis:names:tc:SAML:2.0:nameid-format:transient";
    public static final String UNSPECIFIED = "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";

    public byte[] build(
            String requestId,
            URI destination,
            String issuer,
            URI acs,
            Instant issueInstant,
            Policy policy) {
        requireText(requestId, "requestId");
        requireText(issuer, "issuer");
        java.util.Objects.requireNonNull(destination, "destination");
        java.util.Objects.requireNonNull(acs, "acs");
        java.util.Objects.requireNonNull(issueInstant, "issueInstant");
        java.util.Objects.requireNonNull(policy, "policy");
        var document = SecureXml.newDocument();
        var request = element(document, PROTOCOL, "samlp:AuthnRequest");
        request.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:samlp", PROTOCOL);
        request.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:saml", ASSERTION);
        request.setAttribute("ID", requestId);
        request.setAttribute("Version", "2.0");
        request.setAttribute("IssueInstant", DateTimeFormatter.ISO_INSTANT.format(issueInstant));
        request.setAttribute("Destination", destination.toString());
        request.setAttribute("AssertionConsumerServiceURL", acs.toString());
        request.setAttribute("ProtocolBinding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
        document.appendChild(request);
        var issuerElement = element(document, ASSERTION, "saml:Issuer");
        issuerElement.setTextContent(issuer);
        request.appendChild(issuerElement);
        if (policy.present()) {
            var nameIdPolicy = element(document, PROTOCOL, "samlp:NameIDPolicy");
            if (policy.format() != null) nameIdPolicy.setAttribute("Format", policy.format());
            if (policy.spNameQualifier() != null) {
                nameIdPolicy.setAttribute("SPNameQualifier", policy.spNameQualifier());
            }
            if (policy.allowCreate() != null) {
                nameIdPolicy.setAttribute("AllowCreate", policy.allowCreate().toString());
            }
            request.appendChild(nameIdPolicy);
        }
        return SecureXml.serialize(document);
    }

    public String unknownFormat(String requestId) {
        return "urn:samlier:fixture:unknown-nameid-format:" + token(requestId);
    }

    public String unknownSpNameQualifier(String requestId) {
        return "urn:samlier:fixture:unknown-sp:" + token(requestId);
    }

    private static String token(String requestId) {
        return requestId.startsWith("_") ? requestId.substring(1) : requestId;
    }

    private static Element element(Document document, String namespace, String qualifiedName) {
        return document.createElementNS(namespace, qualifiedName);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    public record Policy(boolean present, String format, String spNameQualifier, Boolean allowCreate) {
        public static Policy omitted() { return new Policy(false, null, null, null); }
        public static Policy empty() { return new Policy(true, null, null, null); }
    }
}
