package org.samlier.saml.normal;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import javax.xml.XMLConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Builds deterministic exact RequestedAuthnContext fixtures without a vendor control API. */
public final class SamlRequestedAuthnContextRequestFactory {
    public static final String PASSWORD_PROTECTED_TRANSPORT =
            "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport";
    public static final String FIXTURE_DECLARATION = "urn:samlier:fixture:authn-context-decl";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";

    public enum Fixture { BASELINE, SATISFIABLE_CLASS, SATISFIABLE_DECLARATION, UNSATISFIABLE_CLASS }

    public byte[] build(
            Fixture fixture,
            String requestId,
            URI destination,
            String issuer,
            URI acs,
            Instant issueInstant) {
        java.util.Objects.requireNonNull(fixture, "fixture");
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
        document.appendChild(request);
        var issuerElement = element(document, ASSERTION, "saml:Issuer");
        issuerElement.setTextContent(issuer);
        request.appendChild(issuerElement);
        if (fixture != Fixture.BASELINE) {
            var requested = element(document, PROTOCOL, "samlp:RequestedAuthnContext");
            requested.setAttribute("Comparison", "exact");
            var reference = switch (fixture) {
                case SATISFIABLE_DECLARATION -> element(
                        document, ASSERTION, "saml:AuthnContextDeclRef");
                case SATISFIABLE_CLASS, UNSATISFIABLE_CLASS -> element(
                        document, ASSERTION, "saml:AuthnContextClassRef");
                case BASELINE -> throw new IllegalStateException("baseline has no context reference");
            };
            reference.setTextContent(switch (fixture) {
                case SATISFIABLE_CLASS -> PASSWORD_PROTECTED_TRANSPORT;
                case SATISFIABLE_DECLARATION -> FIXTURE_DECLARATION;
                case UNSATISFIABLE_CLASS -> "urn:samlier:probe:unavailable-authn-context:"
                        + token(requestId);
                case BASELINE -> throw new IllegalStateException("baseline has no context reference");
            });
            requested.appendChild(reference);
            request.appendChild(requested);
        }
        return SecureXml.serialize(document);
    }

    private Element element(Document document, String namespace, String qualifiedName) {
        return document.createElementNS(namespace, qualifiedName);
    }

    private String token(String requestId) {
        return requestId.startsWith("_") ? requestId.substring(1) : requestId;
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
