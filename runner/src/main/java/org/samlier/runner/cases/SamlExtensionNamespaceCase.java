package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.samlier.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** Passive producer-side namespace checks for SAML extension elements and attributes. */
public final class SamlExtensionNamespaceCase {
    public enum Rule { EXTENSION_ELEMENTS, SUBJECT_CONFIRMATION_DATA_ATTRIBUTES, ATTRIBUTE_ATTRIBUTES }

    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String XMLNS = "http://www.w3.org/2000/xmlns/";
    private static final String SAML_NAMESPACE_PREFIX = "urn:oasis:names:tc:SAML:";
    private static final Set<String> SUBJECT_CONFIRMATION_DATA_ATTRIBUTES = Set.of(
            "NotBefore", "NotOnOrAfter", "Recipient", "InResponseTo", "Address");
    private static final Set<String> ATTRIBUTE_ATTRIBUTES = Set.of("Name", "NameFormat", "FriendlyName");
    private final Rule rule;

    public SamlExtensionNamespaceCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        var code = "saml.extension." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return PassiveXmlCaseSupport.evaluate(messages, code, this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        return switch (rule) {
            case EXTENSION_ELEMENTS -> inspectExtensionElements(document);
            case SUBJECT_CONFIRMATION_DATA_ATTRIBUTES -> inspectExtensionAttributes(
                    document, "SubjectConfirmationData", SUBJECT_CONFIRMATION_DATA_ATTRIBUTES);
            case ATTRIBUTE_ATTRIBUTES -> inspectExtensionAttributes(
                    document, "Attribute", ATTRIBUTE_ATTRIBUTES);
        };
    }

    private PassiveXmlCaseSupport.Inspection inspectExtensionElements(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var extensions = document.getElementsByTagNameNS(PROTOCOL, "Extensions");
        for (var index = 0; index < extensions.getLength(); index++) {
            for (var child = extensions.item(index).getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element element)) continue;
                observed++;
                var namespace = element.getNamespaceURI();
                if (namespace == null || namespace.isBlank() || isSamlNamespace(namespace)) {
                    violations.add("{" + namespace + "}" + element.getLocalName());
                }
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private PassiveXmlCaseSupport.Inspection inspectExtensionAttributes(
            org.w3c.dom.Document document, String localName, Set<String> builtInAttributes) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var elements = document.getElementsByTagNameNS(ASSERTION, localName);
        for (var elementIndex = 0; elementIndex < elements.getLength(); elementIndex++) {
            var element = (Element) elements.item(elementIndex);
            var attributes = element.getAttributes();
            for (var index = 0; index < attributes.getLength(); index++) {
                var attribute = attributes.item(index);
                if (XMLNS.equals(attribute.getNamespaceURI())) continue;
                var namespace = attribute.getNamespaceURI();
                var name = attribute.getLocalName() == null ? attribute.getNodeName() : attribute.getLocalName();
                if ((namespace == null || namespace.isEmpty()) && builtInAttributes.contains(name)) continue;
                observed++;
                if (namespace == null || namespace.isEmpty() || isSamlNamespace(namespace)) {
                    violations.add("{" + namespace + "}" + localName + "/@" + name);
                }
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private boolean isSamlNamespace(String namespace) {
        return namespace.startsWith(SAML_NAMESPACE_PREFIX);
    }
}
