package org.samlier.saml.normal;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import javax.xml.XMLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Builds deterministic AuthnRequest fixtures for the approved IdP error probes and their control. */
public final class SamlErrorProbeRequestFactory {
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";

    public enum Probe {
        PASSIVE_WITHOUT_SESSION,
        BASELINE_SUCCESS,
        UNKNOWN_NAMEID_FORMAT,
        UNSATISFIABLE_AUTHN_CONTEXT
    }

    public byte[] build(
            Probe probe, String requestId, URI destination, String issuer, URI acs, Instant issueInstant) {
        if (probe == null) throw new IllegalArgumentException("probe is required");
        requireText(requestId, "requestId");
        requireText(issuer, "issuer");
        java.util.Objects.requireNonNull(destination, "destination");
        java.util.Objects.requireNonNull(acs, "acs");
        java.util.Objects.requireNonNull(issueInstant, "issueInstant");
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
        if (probe == Probe.PASSIVE_WITHOUT_SESSION) request.setAttribute("IsPassive", "true");
        document.appendChild(request);
        var issuerElement = element(document, ASSERTION, "saml:Issuer");
        issuerElement.setTextContent(issuer);
        request.appendChild(issuerElement);
        switch (probe) {
            case UNKNOWN_NAMEID_FORMAT -> {
                var policy = element(document, PROTOCOL, "samlp:NameIDPolicy");
                policy.setAttribute("Format", unknownNameIdFormat(requestId));
                policy.setAttribute("AllowCreate", "true");
                request.appendChild(policy);
            }
            case UNSATISFIABLE_AUTHN_CONTEXT -> {
                var requested = element(document, PROTOCOL, "samlp:RequestedAuthnContext");
                requested.setAttribute("Comparison", "exact");
                var classRef = element(document, ASSERTION, "saml:AuthnContextClassRef");
                classRef.setTextContent(unavailableAuthnContext(requestId));
                requested.appendChild(classRef);
                request.appendChild(requested);
            }
            case PASSIVE_WITHOUT_SESSION -> { }
            case BASELINE_SUCCESS -> { }
        }
        return SecureXml.serialize(document);
    }

    public String unknownNameIdFormat(String requestId) {
        return "urn:samlier:probe:unknown-nameid-format:" + token(requestId);
    }

    public String unavailableAuthnContext(String requestId) {
        return "urn:samlier:probe:unavailable-authn-context:" + token(requestId);
    }

    private String token(String requestId) {
        return requestId.startsWith("_") ? requestId.substring(1) : requestId;
    }

    private Element element(Document document, String namespace, String qualifiedName) {
        return document.createElementNS(namespace, qualifiedName);
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
