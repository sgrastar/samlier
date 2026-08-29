package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.saml.normal.SamlSchemaValidation;
import org.samlier.saml.normal.SamlSchemaValidation.SchemaKind;
import org.w3c.dom.Element;

/** Passive, schema-type-aware enforcement of the SAML non-empty string rule. */
public final class SamlStringValueCase {
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        return PassiveXmlCaseSupport.evaluate(messages, "saml.string.non-empty", this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var root = document.getDocumentElement();
        var kind = schemaKind(root);
        if (kind == null) return new PassiveXmlCaseSupport.Inspection(0, List.of());
        var inspection = SamlSchemaValidation.inspectStringValues(root, kind);
        if (!inspection.schemaValid()) {
            return new PassiveXmlCaseSupport.Inspection(0, List.of(), List.of("schema-type-information"));
        }
        var violations = new ArrayList<String>();
        var observed = 0;
        for (var value : inspection.values()) {
            if (isEmptyAttributeValueException(value)) continue;
            observed++;
            if (value.value().strip().isEmpty()) violations.add(value.path());
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private SchemaKind schemaKind(Element root) {
        if (PROTOCOL.equals(root.getNamespaceURI())) return SchemaKind.PROTOCOL;
        if (ASSERTION.equals(root.getNamespaceURI())) return SchemaKind.ASSERTION;
        return null;
    }

    private boolean isEmptyAttributeValueException(SamlSchemaValidation.TypedStringValue value) {
        return !value.attribute()
                && value.path().endsWith("/{" + ASSERTION + "}AttributeValue")
                && value.value().isEmpty();
    }
}
