package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.saml.normal.SamlSchemaValidation;
import org.samlier.saml.normal.SamlSchemaValidation.SchemaKind;
import org.w3c.dom.Element;

/** Passive schema checks for target-generated AuthnRequest, Response, and Assertion elements. */
public final class SamlSchemaConformanceCase {
    public enum Rule { AUTHN_REQUEST, RESPONSE }

    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private final Rule rule;

    public SamlSchemaConformanceCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        var code = "saml.schema." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return PassiveXmlCaseSupport.evaluate(messages, code, this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var namespace = PROTOCOL;
        var localName = switch (rule) {
            case AUTHN_REQUEST -> "AuthnRequest";
            case RESPONSE -> "Response";
        };
        var kind = SchemaKind.PROTOCOL;
        var observed = 0;
        var violations = new ArrayList<String>();
        var elements = document.getElementsByTagNameNS(namespace, localName);
        for (var index = 0; index < elements.getLength(); index++) {
            observed++;
            var element = (Element) elements.item(index);
            if (!SamlSchemaValidation.isValid(element, kind)
                    || !"2.0".equals(element.getAttribute("Version"))
                    || !element.getAttribute("IssueInstant").endsWith("Z")) {
                violations.add("{" + namespace + "}" + localName);
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }
}
