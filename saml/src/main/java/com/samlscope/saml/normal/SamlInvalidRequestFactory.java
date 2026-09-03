package com.samlscope.saml.normal;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import javax.xml.XMLConstants;

/** Builds deterministic malformed-request fixtures whose browser response is correlated by RelayState. */
public final class SamlInvalidRequestFactory {
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";

    public enum Fixture { BASELINE, MISSING_ID, UNSUPPORTED_VERSION }

    public byte[] build(
            Fixture fixture,
            String requestId,
            URI destination,
            String issuer,
            URI acs,
            Instant issueInstant) {
        java.util.Objects.requireNonNull(fixture, "fixture");
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("issuer is required");
        java.util.Objects.requireNonNull(destination, "destination");
        java.util.Objects.requireNonNull(acs, "acs");
        java.util.Objects.requireNonNull(issueInstant, "issueInstant");
        var document = SecureXml.newDocument();
        var request = document.createElementNS(PROTOCOL, "samlp:AuthnRequest");
        request.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:samlp", PROTOCOL);
        request.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:saml", ASSERTION);
        if (fixture != Fixture.MISSING_ID) request.setAttribute("ID", requestId);
        request.setAttribute("Version", fixture == Fixture.UNSUPPORTED_VERSION ? "1.1" : "2.0");
        request.setAttribute("IssueInstant", DateTimeFormatter.ISO_INSTANT.format(issueInstant));
        request.setAttribute("Destination", destination.toString());
        request.setAttribute("AssertionConsumerServiceURL", acs.toString());
        request.setAttribute("ProtocolBinding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
        document.appendChild(request);
        var issuerElement = document.createElementNS(ASSERTION, "saml:Issuer");
        issuerElement.setTextContent(issuer);
        request.appendChild(issuerElement);
        return SecureXml.serialize(document);
    }
}
