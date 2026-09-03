package com.samlscope.runner.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import com.samlscope.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** IIP-SSO01.eg/ei: passive lexical inspection of SAML-defined xs:dateTime fields. */
public final class SamlTimeValueCase {
    public enum Rule { UTC_REPRESENTATION, NO_LEAP_SECOND }

    private static final String ASSERTION = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String METADATA = "urn:oasis:names:tc:SAML:2.0:metadata";
    private static final Set<String> TIME_ATTRIBUTES = Set.of(
            "IssueInstant", "NotBefore", "NotOnOrAfter", "AuthnInstant", "SessionNotOnOrAfter", "validUntil");
    private static final Pattern SECOND = Pattern.compile("T\\d{2}:\\d{2}:(\\d{2})(?:[.,]\\d+)?(?:Z|[+-]\\d{2}:\\d{2})?$");

    private final Rule rule;

    public SamlTimeValueCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        var code = rule == Rule.UTC_REPRESENTATION ? "saml.time.utc" : "saml.time.leap-second";
        return PassiveXmlCaseSupport.evaluate(messages, code, this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var elements = document.getElementsByTagNameNS("*", "*");
        for (var index = 0; index < elements.getLength(); index++) {
            var element = (Element) elements.item(index);
            if (!samlNamespace(element.getNamespaceURI())) continue;
            for (var name : TIME_ATTRIBUTES) {
                if (!element.hasAttribute(name) || !attributeBelongsHere(element, name)) continue;
                observed++;
                var value = element.getAttribute(name);
                if (violates(value)) {
                    violations.add("{" + element.getNamespaceURI() + "}" + element.getLocalName() + "/@" + name);
                }
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private boolean attributeBelongsHere(Element element, String name) {
        if ("validUntil".equals(name)) return METADATA.equals(element.getNamespaceURI());
        return ASSERTION.equals(element.getNamespaceURI()) || PROTOCOL.equals(element.getNamespaceURI());
    }

    private boolean violates(String value) {
        if (rule == Rule.UTC_REPRESENTATION) return value == null || !value.endsWith("Z");
        var matcher = SECOND.matcher(value == null ? "" : value);
        return matcher.find() && Integer.parseInt(matcher.group(1)) >= 60;
    }

    private boolean samlNamespace(String namespace) {
        return ASSERTION.equals(namespace) || PROTOCOL.equals(namespace) || METADATA.equals(namespace);
    }
}
