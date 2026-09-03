package com.samlscope.runner.cases;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.samlscope.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** IIP-SSO01.ef: passive lexical inspection of every SAML-defined xs:anyURI field. */
public final class SamlUriValueCase {
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String METADATA = "urn:oasis:names:tc:SAML:2.0:metadata";

    private static final Set<String> ASSERTION_URI_ELEMENTS = Set.of(
            "AssertionURIRef", "Audience", "AuthnContextClassRef", "AuthnContextDeclRef",
            "AuthenticatingAuthority");
    private static final Set<String> PROTOCOL_URI_ELEMENTS = Set.of("RequesterID", "GetComplete");
    private static final Set<String> METADATA_URI_ELEMENTS = Set.of(
            "OrganizationURL", "EmailAddress", "AdditionalMetadataLocation", "NameIDFormat",
            "AttributeProfile", "AffiliateMember");
    private static final Set<String> NAME_ID_ELEMENTS = Set.of("Issuer", "NameID");

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        return PassiveXmlCaseSupport.evaluate(messages, "saml.uri", this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var values = new ArrayList<Field>();
        var elements = document.getElementsByTagNameNS("*", "*");
        for (var index = 0; index < elements.getLength(); index++) {
            var element = (Element) elements.item(index);
            collectElementValue(element, values);
            collectAttributeValues(element, values);
        }
        var violations = values.stream()
                .filter(field -> !absolute(field.value()))
                .map(Field::path)
                .toList();
        return new PassiveXmlCaseSupport.Inspection(values.size(), violations);
    }

    private void collectElementValue(Element element, List<Field> values) {
        var namespace = element.getNamespaceURI();
        var local = element.getLocalName();
        var uriElement = ASSERTION.equals(namespace) && ASSERTION_URI_ELEMENTS.contains(local)
                || PROTOCOL.equals(namespace) && PROTOCOL_URI_ELEMENTS.contains(local)
                || METADATA.equals(namespace) && METADATA_URI_ELEMENTS.contains(local);
        if (uriElement) values.add(new Field(path(element), element.getTextContent()));
    }

    private void collectAttributeValues(Element element, List<Field> values) {
        var namespace = element.getNamespaceURI();
        var local = element.getLocalName();
        if (ASSERTION.equals(namespace)) {
            if (NAME_ID_ELEMENTS.contains(local)) attribute(element, "Format", values);
            if ("SubjectConfirmation".equals(local)) attribute(element, "Method", values);
            if ("SubjectConfirmationData".equals(local)) attribute(element, "Recipient", values);
            if ("AuthzDecisionStatement".equals(local)) attribute(element, "Resource", values);
            if ("Action".equals(local)) attribute(element, "Namespace", values);
            if ("Attribute".equals(local)) attribute(element, "NameFormat", values);
        } else if (PROTOCOL.equals(namespace)) {
            attribute(element, "Destination", values);
            attribute(element, "Consent", values);
            if ("StatusCode".equals(local)) attribute(element, "Value", values);
            if ("AuthzDecisionQuery".equals(local)) attribute(element, "Resource", values);
            if ("AuthnRequest".equals(local)) {
                attribute(element, "ProtocolBinding", values);
                attribute(element, "AssertionConsumerServiceURL", values);
            }
            if ("NameIDPolicy".equals(local)) attribute(element, "Format", values);
            if ("IDPEntry".equals(local)) {
                attribute(element, "ProviderID", values);
                attribute(element, "Loc", values);
            }
        } else if (METADATA.equals(namespace)) {
            if ("EntityDescriptor".equals(local)) attribute(element, "entityID", values);
            if ("AffiliationDescriptor".equals(local)) attribute(element, "affiliationOwnerID", values);
            attribute(element, "Binding", values);
            attribute(element, "Location", values);
            attribute(element, "ResponseLocation", values);
            if ("AdditionalMetadataLocation".equals(local)) attribute(element, "namespace", values);
            attributeList(element, "protocolSupportEnumeration", values);
            attribute(element, "errorURL", values);
        }
    }

    private void attribute(Element element, String name, List<Field> values) {
        if (element.hasAttribute(name)) values.add(new Field(path(element) + "/@" + name, element.getAttribute(name)));
    }

    private void attributeList(Element element, String name, List<Field> values) {
        if (!element.hasAttribute(name)) return;
        var raw = element.getAttribute(name);
        if (raw.isBlank()) {
            values.add(new Field(path(element) + "/@" + name, raw));
            return;
        }
        for (var value : raw.trim().split("\\s+")) {
            values.add(new Field(path(element) + "/@" + name, value));
        }
    }

    private boolean absolute(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            return new URI(value).isAbsolute();
        } catch (java.net.URISyntaxException invalid) {
            return false;
        }
    }

    private String path(Element element) {
        return "{" + element.getNamespaceURI() + "}" + element.getLocalName();
    }

    private record Field(String path, String value) {}
}
