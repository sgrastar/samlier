package com.samlscope.runner.cases;

import java.util.ArrayList;
import java.util.List;
import com.samlscope.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** Passive checks for qualifier attributes on formats that do not define their semantics. */
public final class SamlQualifierOmissionCase {
    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String UNSPECIFIED = "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified";

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        return PassiveXmlCaseSupport.evaluate(messages, "saml.qualifier.omission", this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var issuers = document.getElementsByTagNameNS(ASSERTION, "Issuer");
        for (var index = 0; index < issuers.getLength(); index++) {
            var issuer = (Element) issuers.item(index);
            observed++;
            checkQualifiers(issuer, "Issuer", violations);
        }
        var identifiers = document.getElementsByTagNameNS(ASSERTION, "NameID");
        for (var index = 0; index < identifiers.getLength(); index++) {
            var identifier = (Element) identifiers.item(index);
            var format = identifier.getAttribute("Format");
            if (!format.isEmpty() && !UNSPECIFIED.equals(format)) continue;
            observed++;
            checkQualifiers(identifier, "NameID", violations);
        }
        var baseIdentifiers = document.getElementsByTagNameNS(ASSERTION, "BaseID");
        for (var index = 0; index < baseIdentifiers.getLength(); index++) {
            observed++;
            checkQualifiers((Element) baseIdentifiers.item(index), "BaseID", violations);
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private void checkQualifiers(Element element, String localName, List<String> violations) {
        for (var attribute : List.of("NameQualifier", "SPNameQualifier")) {
            if (element.hasAttribute(attribute)) {
                violations.add("{" + ASSERTION + "}" + localName + "/@" + attribute);
            }
        }
    }
}
