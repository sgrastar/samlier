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
        PASSIVE_WITH_SESSION,
        FORCE_AUTHN_PASSIVE,
        FORCE_AUTHN_FALSE,
        FORCE_AUTHN_TRUE,
        SUBMILLISECOND_ISSUE_INSTANT,
        VERSION_1_1,
        VERSION_3_0,
        BASELINE_SUCCESS,
        UNKNOWN_NAMEID_FORMAT,
        UNKNOWN_EXTENSION,
        UNKNOWN_ANY_ATTRIBUTE,
        STRING_BOUNDARY_255,
        STRING_BOUNDARY_256,
        DTD_AUTHN_REQUEST,
        DTD_EXTERNAL_ENTITY_AUTHN_REQUEST,
        ACS_SELECTION_OMITTED,
        ISSUER_TRAILING_WHITESPACE,
        PERSISTENT_NAMEID_POLICY,
        UNRECOGNIZED_SUBJECT,
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
        request.setAttribute("Version", switch (probe) {
            case VERSION_1_1 -> "1.1";
            case VERSION_3_0 -> "3.0";
            default -> "2.0";
        });
        request.setAttribute("IssueInstant", DateTimeFormatter.ISO_INSTANT.format(
                probe == Probe.SUBMILLISECOND_ISSUE_INSTANT
                        ? issueInstant.plusNanos(123_456) : issueInstant));
        request.setAttribute("Destination", destination.toString());
        if (probe != Probe.ACS_SELECTION_OMITTED) {
            request.setAttribute("AssertionConsumerServiceURL", acs.toString());
            request.setAttribute("ProtocolBinding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
        }
        if (probe == Probe.STRING_BOUNDARY_255 || probe == Probe.STRING_BOUNDARY_256) {
            var length = probe == Probe.STRING_BOUNDARY_255 ? 255 : 256;
            request.setAttribute("ProviderName", "\u0416".repeat(length));
        }
        if (probe == Probe.PASSIVE_WITHOUT_SESSION || probe == Probe.PASSIVE_WITH_SESSION
                || probe == Probe.FORCE_AUTHN_PASSIVE) request.setAttribute("IsPassive", "true");
        if (probe == Probe.FORCE_AUTHN_PASSIVE) request.setAttribute("ForceAuthn", "true");
        if (probe == Probe.FORCE_AUTHN_FALSE) request.setAttribute("ForceAuthn", "false");
        if (probe == Probe.FORCE_AUTHN_TRUE) request.setAttribute("ForceAuthn", "true");
        document.appendChild(request);
        var issuerElement = element(document, ASSERTION, "saml:Issuer");
        issuerElement.setTextContent(probe == Probe.ISSUER_TRAILING_WHITESPACE ? issuer + " " : issuer);
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
            case UNKNOWN_EXTENSION -> {
                var extensions = element(document, PROTOCOL, "samlp:Extensions");
                var unknown = element(document, "urn:samlier:probe:unknown-extension", "probe:UnknownExtension");
                unknown.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:probe",
                        "urn:samlier:probe:unknown-extension");
                unknown.setAttribute("fixture", token(requestId));
                extensions.appendChild(unknown);
                request.appendChild(extensions);
            }
            case UNKNOWN_ANY_ATTRIBUTE -> {
                var subject = element(document, ASSERTION, "saml:Subject");
                var confirmation = element(document, ASSERTION, "saml:SubjectConfirmation");
                confirmation.setAttribute("Method", "urn:samlier:probe:confirmation-method");
                var data = element(document, ASSERTION, "saml:SubjectConfirmationData");
                data.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:probe",
                        "urn:samlier:probe:unknown-attribute");
                data.setAttributeNS(
                        "urn:samlier:probe:unknown-attribute", "probe:fixture", token(requestId));
                confirmation.appendChild(data);
                subject.appendChild(confirmation);
                request.appendChild(subject);
            }
            case PERSISTENT_NAMEID_POLICY -> {
                var policy = element(document, PROTOCOL, "samlp:NameIDPolicy");
                policy.setAttribute("Format", "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent");
                policy.setAttribute("AllowCreate", "true");
                request.appendChild(policy);
            }
            case UNRECOGNIZED_SUBJECT -> {
                var subject = element(document, ASSERTION, "saml:Subject");
                var nameId = element(document, ASSERTION, "saml:NameID");
                nameId.setAttribute("Format", "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent");
                nameId.setTextContent("urn:samlier:probe:unknown-subject:" + token(requestId));
                subject.appendChild(nameId);
                request.appendChild(subject);
            }
            case PASSIVE_WITHOUT_SESSION -> { }
            case PASSIVE_WITH_SESSION -> { }
            case FORCE_AUTHN_PASSIVE -> { }
            case FORCE_AUTHN_FALSE -> { }
            case FORCE_AUTHN_TRUE -> { }
            case SUBMILLISECOND_ISSUE_INSTANT -> { }
            case VERSION_1_1 -> { }
            case VERSION_3_0 -> { }
            case BASELINE_SUCCESS -> { }
            case STRING_BOUNDARY_255 -> { }
            case STRING_BOUNDARY_256 -> { }
            case DTD_AUTHN_REQUEST -> { }
            case DTD_EXTERNAL_ENTITY_AUTHN_REQUEST -> { }
            case ACS_SELECTION_OMITTED -> { }
            case ISSUER_TRAILING_WHITESPACE -> { }
        }
        var serialized = SecureXml.serialize(document);
        if (probe == Probe.DTD_AUTHN_REQUEST || probe == Probe.DTD_EXTERNAL_ENTITY_AUTHN_REQUEST) {
            var xml = new String(serialized, java.nio.charset.StandardCharsets.UTF_8);
            var declarationEnd = xml.indexOf("?>");
            var insertion = probe == Probe.DTD_AUTHN_REQUEST
                    ? "<!DOCTYPE samlp:AuthnRequest>"
                    : "<!DOCTYPE samlp:AuthnRequest [<!ENTITY samlier SYSTEM \"https://invalid.example/samlier.dtd\">]>";
            xml = declarationEnd >= 0
                    ? xml.substring(0, declarationEnd + 2) + insertion + xml.substring(declarationEnd + 2)
                    : insertion + xml;
            return xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return serialized;
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
