package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.samlier.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Passive structural rules from SAML2Core that do not require schema type inference. */
public final class SamlCoreStructureCase {
    public enum Rule {
        TOP_LEVEL_STATUS_CODE,
        GENERIC_STATEMENT_TYPE,
        SUBJECT_WITHOUT_STATEMENTS,
        GENERIC_CONDITION_TYPE,
        ONE_TIME_USE_LIMIT,
        PROXY_RESTRICTION_LIMIT,
        SUBJECT_FOR_AUTHN_STATEMENT,
        SUBJECT_FOR_ATTRIBUTE_STATEMENT
    }

    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final Set<String> TOP_STATUS = Set.of(
            "urn:oasis:names:tc:SAML:2.0:status:Success",
            "urn:oasis:names:tc:SAML:2.0:status:Requester",
            "urn:oasis:names:tc:SAML:2.0:status:Responder",
            "urn:oasis:names:tc:SAML:2.0:status:VersionMismatch");
    private static final Set<String> STATEMENTS = Set.of(
            "Statement", "AuthnStatement", "AuthzDecisionStatement", "AttributeStatement");

    private final Rule rule;

    public SamlCoreStructureCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        var code = "saml.structure." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return PassiveXmlCaseSupport.evaluate(messages, code, this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        return switch (rule) {
            case TOP_LEVEL_STATUS_CODE -> topStatus(document);
            case GENERIC_STATEMENT_TYPE -> requiredXsiType(document, "Statement");
            case SUBJECT_WITHOUT_STATEMENTS -> subjectForStatementCondition(document, null, true);
            case GENERIC_CONDITION_TYPE -> requiredXsiType(document, "Condition");
            case ONE_TIME_USE_LIMIT -> childLimit(document, "Conditions", "OneTimeUse");
            case PROXY_RESTRICTION_LIMIT -> childLimit(document, "Conditions", "ProxyRestriction");
            case SUBJECT_FOR_AUTHN_STATEMENT -> subjectForStatementCondition(document, "AuthnStatement", false);
            case SUBJECT_FOR_ATTRIBUTE_STATEMENT -> subjectForStatementCondition(document, "AttributeStatement", false);
        };
    }

    private PassiveXmlCaseSupport.Inspection topStatus(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var statuses = document.getElementsByTagNameNS(PROTOCOL, "Status");
        for (var index = 0; index < statuses.getLength(); index++) {
            var code = directChild((Element) statuses.item(index), PROTOCOL, "StatusCode");
            if (code == null) continue;
            observed++;
            if (!TOP_STATUS.contains(code.getAttribute("Value"))) {
                violations.add("{" + PROTOCOL + "}Status/StatusCode/@Value");
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private PassiveXmlCaseSupport.Inspection requiredXsiType(
            org.w3c.dom.Document document, String localName) {
        var elements = document.getElementsByTagNameNS(ASSERTION, localName);
        var violations = new ArrayList<String>();
        for (var index = 0; index < elements.getLength(); index++) {
            var element = (Element) elements.item(index);
            if (element.getAttributeNS(XSI, "type").isBlank()) {
                violations.add("{" + ASSERTION + "}" + localName + "/@xsi:type");
            }
        }
        return new PassiveXmlCaseSupport.Inspection(elements.getLength(), violations);
    }

    private PassiveXmlCaseSupport.Inspection childLimit(
            org.w3c.dom.Document document, String parentName, String childName) {
        var parents = document.getElementsByTagNameNS(ASSERTION, parentName);
        var violations = new ArrayList<String>();
        for (var index = 0; index < parents.getLength(); index++) {
            var count = directChildren((Element) parents.item(index), ASSERTION, childName);
            if (count > 1) violations.add("{" + ASSERTION + "}" + parentName + "/" + childName);
        }
        return new PassiveXmlCaseSupport.Inspection(parents.getLength(), violations);
    }

    private PassiveXmlCaseSupport.Inspection subjectForStatementCondition(
            org.w3c.dom.Document document, String requiredStatement, boolean requireNoStatements) {
        var assertions = document.getElementsByTagNameNS(ASSERTION, "Assertion");
        var observed = 0;
        var violations = new ArrayList<String>();
        for (var index = 0; index < assertions.getLength(); index++) {
            var assertion = (Element) assertions.item(index);
            var trigger = requireNoStatements
                    ? directChildrenInSet(assertion, ASSERTION, STATEMENTS) == 0
                    : directChildren(assertion, ASSERTION, requiredStatement) > 0;
            if (!trigger) continue;
            observed++;
            if (directChild(assertion, ASSERTION, "Subject") == null) {
                violations.add("{" + ASSERTION + "}Assertion/Subject");
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private Element directChild(Element parent, String namespace, String localName) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private int directChildren(Element parent, String namespace, String localName) {
        var count = 0;
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) count++;
        }
        return count;
    }

    private int directChildrenInSet(Element parent, String namespace, Set<String> localNames) {
        var count = 0;
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                var element = (Element) child;
                if (namespace.equals(element.getNamespaceURI()) && localNames.contains(element.getLocalName())) count++;
            }
        }
        return count;
    }
}
