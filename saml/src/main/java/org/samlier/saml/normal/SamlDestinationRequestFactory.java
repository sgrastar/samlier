package org.samlier.saml.normal;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import javax.xml.XMLConstants;

/** Builds valid AuthnRequests whose Destination is varied independently of the delivery URI. */
public final class SamlDestinationRequestFactory {
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";

    public enum Fixture { BASELINE, DIFFERENT_HOST, OTHER_TARGET_ENDPOINT, OTHER_IDP }

    public byte[] build(
            Fixture fixture,
            String requestId,
            URI actualEndpoint,
            String issuer,
            URI acs,
            Instant issueInstant) {
        java.util.Objects.requireNonNull(fixture, "fixture");
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("issuer is required");
        java.util.Objects.requireNonNull(actualEndpoint, "actualEndpoint");
        java.util.Objects.requireNonNull(acs, "acs");
        java.util.Objects.requireNonNull(issueInstant, "issueInstant");
        var document = SecureXml.newDocument();
        var request = document.createElementNS(PROTOCOL, "samlp:AuthnRequest");
        request.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:samlp", PROTOCOL);
        request.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:saml", ASSERTION);
        request.setAttribute("ID", requestId);
        request.setAttribute("Version", "2.0");
        request.setAttribute("IssueInstant", DateTimeFormatter.ISO_INSTANT.format(issueInstant));
        request.setAttribute("Destination", destination(fixture, actualEndpoint).toString());
        request.setAttribute("AssertionConsumerServiceURL", acs.toString());
        request.setAttribute("ProtocolBinding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
        document.appendChild(request);
        var issuerElement = document.createElementNS(ASSERTION, "saml:Issuer");
        issuerElement.setTextContent(issuer);
        request.appendChild(issuerElement);
        return SecureXml.serialize(document);
    }

    URI destination(Fixture fixture, URI actual) {
        try {
            return switch (fixture) {
                case BASELINE -> actual;
                case DIFFERENT_HOST -> new URI(
                        actual.getScheme(), actual.getUserInfo(),
                        loopbackAlternative(actual.getHost()), actual.getPort(),
                        actual.getPath(), actual.getQuery(), actual.getFragment());
                case OTHER_TARGET_ENDPOINT -> new URI(
                        actual.getScheme(), actual.getUserInfo(), actual.getHost(), actual.getPort(),
                        "/samlier-fixture/not-the-sso-endpoint", null, null);
                case OTHER_IDP -> URI.create("https://other-idp.invalid/sso");
            };
        } catch (URISyntaxException impossible) {
            throw new IllegalArgumentException("Cannot derive Destination fixture", impossible);
        }
    }

    private String loopbackAlternative(String host) {
        if (host == null || host.isBlank()) return "destination-mismatch.invalid";
        if ("localhost".equalsIgnoreCase(host)) return "127.0.0.1";
        if ("127.0.0.1".equals(host) || "::1".equals(host)) return "localhost";
        return "destination-mismatch.invalid";
    }
}
