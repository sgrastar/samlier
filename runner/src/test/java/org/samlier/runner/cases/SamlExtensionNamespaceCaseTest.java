package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;

class SamlExtensionNamespaceCaseTest {
    @Test
    void extensionChildrenRequireAnExternalNamespace() {
        assertOutcome(SamlExtensionNamespaceCase.Rule.EXTENSION_ELEMENTS, Outcome.SATISFIED, extensions(
                "<ext:Value xmlns:ext=\"urn:example:extension\"/>"));
        assertOutcome(SamlExtensionNamespaceCase.Rule.EXTENSION_ELEMENTS, Outcome.VIOLATED, extensions(
                "<Value/>"));
        assertOutcome(SamlExtensionNamespaceCase.Rule.EXTENSION_ELEMENTS, Outcome.VIOLATED, extensions(
                "<saml:NameID xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\"/>"));
        assertOutcome(SamlExtensionNamespaceCase.Rule.EXTENSION_ELEMENTS, Outcome.SATISFIED_WITH_NOTE,
                extensions(""));
    }

    @Test
    void subjectConfirmationDataAllowsBuiltInsAndExternallyQualifiedExtensions() {
        assertOutcome(SamlExtensionNamespaceCase.Rule.SUBJECT_CONFIRMATION_DATA_ATTRIBUTES, Outcome.SATISFIED_WITH_NOTE,
                subjectConfirmationData("Recipient=\"https://sp.example/acs\""));
        assertOutcome(SamlExtensionNamespaceCase.Rule.SUBJECT_CONFIRMATION_DATA_ATTRIBUTES, Outcome.SATISFIED,
                subjectConfirmationData("xmlns:ext=\"urn:example:extension\" ext:flag=\"yes\""));
        assertOutcome(SamlExtensionNamespaceCase.Rule.SUBJECT_CONFIRMATION_DATA_ATTRIBUTES, Outcome.VIOLATED,
                subjectConfirmationData("custom=\"bad\""));
        assertOutcome(SamlExtensionNamespaceCase.Rule.SUBJECT_CONFIRMATION_DATA_ATTRIBUTES, Outcome.VIOLATED,
                subjectConfirmationData("xmlns:samlx=\"urn:oasis:names:tc:SAML:2.0:assertion\" samlx:custom=\"bad\""));
    }

    @Test
    void attributeAllowsBuiltInsButNotLocalOrSamlExtensionAttributes() {
        assertOutcome(SamlExtensionNamespaceCase.Rule.ATTRIBUTE_ATTRIBUTES, Outcome.SATISFIED,
                attribute("Name=\"role\" xmlns:ext=\"urn:example:extension\" ext:source=\"hr\""));
        assertOutcome(SamlExtensionNamespaceCase.Rule.ATTRIBUTE_ATTRIBUTES, Outcome.VIOLATED,
                attribute("Name=\"role\" custom=\"bad\""));
    }

    private String extensions(String child) {
        return "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"><samlp:Extensions>"
                + child + "</samlp:Extensions></samlp:AuthnRequest>";
    }

    private String subjectConfirmationData(String attributes) {
        return "<saml:SubjectConfirmationData xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
                + attributes + "/>";
    }

    private String attribute(String attributes) {
        return "<saml:Attribute xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" " + attributes + "/>";
    }

    private void assertOutcome(SamlExtensionNamespaceCase.Rule rule, Outcome expected, String xml) {
        var outcome = new SamlExtensionNamespaceCase(rule).evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome(), rule.name());
    }
}
