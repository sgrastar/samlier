package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import org.samlier.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** Passive time-order and containment checks for IIP-SSO01.cn/db/dc. */
public final class SamlTimeRelationshipCase {
    public enum Rule { CONDITIONS_ORDER, CONFIRMATION_WITHIN_CONDITIONS, CONFIRMATION_ORDER }

    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final DatatypeFactory DATES = factory();
    private final Rule rule;

    public SamlTimeRelationshipCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        var code = "saml.time." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return PassiveXmlCaseSupport.evaluate(messages, code, this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        return switch (rule) {
            case CONDITIONS_ORDER -> compareWithin(document, "Conditions");
            case CONFIRMATION_ORDER -> compareWithin(document, "SubjectConfirmationData");
            case CONFIRMATION_WITHIN_CONDITIONS -> confirmationWithinConditions(document);
        };
    }

    private PassiveXmlCaseSupport.Inspection compareWithin(
            org.w3c.dom.Document document, String localName) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var unverifiable = new ArrayList<String>();
        var elements = document.getElementsByTagNameNS(ASSERTION, localName);
        for (var index = 0; index < elements.getLength(); index++) {
            var element = (Element) elements.item(index);
            if (!element.hasAttribute("NotBefore") || !element.hasAttribute("NotOnOrAfter")) continue;
            observed++;
            var path = "{" + ASSERTION + "}" + localName;
            var comparison = compare(element.getAttribute("NotBefore"), element.getAttribute("NotOnOrAfter"));
            if (comparison == null) unverifiable.add(path);
            else if (comparison != DatatypeConstants.LESSER) violations.add(path);
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations, unverifiable);
    }

    private PassiveXmlCaseSupport.Inspection confirmationWithinConditions(
            org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var unverifiable = new ArrayList<String>();
        var assertions = document.getElementsByTagNameNS(ASSERTION, "Assertion");
        for (var assertionIndex = 0; assertionIndex < assertions.getLength(); assertionIndex++) {
            var assertion = (Element) assertions.item(assertionIndex);
            var conditions = directChild(assertion, "Conditions");
            if (conditions == null) continue;
            var confirmations = assertion.getElementsByTagNameNS(ASSERTION, "SubjectConfirmationData");
            for (var index = 0; index < confirmations.getLength(); index++) {
                var confirmation = (Element) confirmations.item(index);
                var path = "{" + ASSERTION + "}SubjectConfirmationData";
                if (conditions.hasAttribute("NotBefore") && confirmation.hasAttribute("NotBefore")) {
                    observed++;
                    var comparison = compare(confirmation.getAttribute("NotBefore"), conditions.getAttribute("NotBefore"));
                    if (comparison == null) unverifiable.add(path + "/@NotBefore");
                    else if (comparison == DatatypeConstants.LESSER) violations.add(path + "/@NotBefore");
                }
                if (conditions.hasAttribute("NotOnOrAfter") && confirmation.hasAttribute("NotOnOrAfter")) {
                    observed++;
                    var comparison = compare(confirmation.getAttribute("NotOnOrAfter"), conditions.getAttribute("NotOnOrAfter"));
                    if (comparison == null) unverifiable.add(path + "/@NotOnOrAfter");
                    else if (comparison == DatatypeConstants.GREATER) violations.add(path + "/@NotOnOrAfter");
                }
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations, unverifiable);
    }

    private Integer compare(String left, String right) {
        try {
            var comparison = DATES.newXMLGregorianCalendar(left).compare(DATES.newXMLGregorianCalendar(right));
            return comparison == DatatypeConstants.INDETERMINATE ? null : comparison;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private Element directChild(Element parent, String localName) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && ASSERTION.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private static DatatypeFactory factory() {
        try {
            return DatatypeFactory.newInstance();
        } catch (javax.xml.datatype.DatatypeConfigurationException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }
}
