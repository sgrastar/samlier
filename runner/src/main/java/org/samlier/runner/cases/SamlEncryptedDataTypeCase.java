package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import org.samlier.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** Passive checks for xenc:EncryptedData/@Type on SAML encrypted elements. */
public final class SamlEncryptedDataTypeCase {
    public enum Rule { TYPE_PRESENT, TYPE_IS_ELEMENT }

    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String XMLENC = "http://www.w3.org/2001/04/xmlenc#";
    private static final String ELEMENT_TYPE = XMLENC + "Element";
    private final Rule rule;

    public SamlEncryptedDataTypeCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        var code = "saml.encrypted-data-type." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return PassiveXmlCaseSupport.evaluate(messages, code, this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        for (var localName : List.of("EncryptedAssertion", "EncryptedID", "EncryptedAttribute")) {
            var encryptedElements = document.getElementsByTagNameNS(ASSERTION, localName);
            for (var index = 0; index < encryptedElements.getLength(); index++) {
                var encrypted = (Element) encryptedElements.item(index);
                var encryptedData = directEncryptedData(encrypted);
                if (rule == Rule.TYPE_PRESENT) {
                    observed++;
                    if (encryptedData == null || !encryptedData.hasAttribute("Type")) {
                        violations.add("{" + ASSERTION + "}" + localName + "/xenc:EncryptedData/@Type");
                    }
                } else if (encryptedData != null && encryptedData.hasAttribute("Type")) {
                    observed++;
                    if (!ELEMENT_TYPE.equals(encryptedData.getAttribute("Type"))) {
                        violations.add("{" + ASSERTION + "}" + localName + "/xenc:EncryptedData/@Type");
                    }
                }
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private Element directEncryptedData(Element parent) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && XMLENC.equals(element.getNamespaceURI())
                    && "EncryptedData".equals(element.getLocalName())) return element;
        }
        return null;
    }
}
