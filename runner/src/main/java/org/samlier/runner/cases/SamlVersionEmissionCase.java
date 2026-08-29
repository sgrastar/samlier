package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import org.samlier.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** Passive producer-side version checks for IIP-SSO01.ej/eq/fg. */
public final class SamlVersionEmissionCase {
    public enum Rule { ASSERTIONS_SUPPORTED, NO_V1_ASSERTION_IN_V2_RESPONSE, AUTHN_REQUEST_HIGHEST }

    private static final String ASSERTION_2 = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String ASSERTION_1 = "urn:oasis:names:tc:SAML:1.0:assertion";
    private static final String PROTOCOL_2 = "urn:oasis:names:tc:SAML:2.0:protocol";
    private final Rule rule;

    public SamlVersionEmissionCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        var code = "saml.version." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return PassiveXmlCaseSupport.evaluate(messages, code, this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        return switch (rule) {
            case ASSERTIONS_SUPPORTED -> assertionsSupported(document);
            case NO_V1_ASSERTION_IN_V2_RESPONSE -> noV1InV2Response(document);
            case AUTHN_REQUEST_HIGHEST -> authnRequestVersion(document);
        };
    }

    private PassiveXmlCaseSupport.Inspection assertionsSupported(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var elements = document.getElementsByTagNameNS("*", "Assertion");
        for (var index = 0; index < elements.getLength(); index++) {
            var assertion = (Element) elements.item(index);
            var namespace = assertion.getNamespaceURI();
            if (namespace == null || !namespace.startsWith("urn:oasis:names:tc:SAML:")) continue;
            if (!assertion.hasAttribute("Version")) continue;
            observed++;
            if (!ASSERTION_2.equals(namespace) || !"2.0".equals(assertion.getAttribute("Version"))) {
                violations.add("{" + namespace + "}Assertion/@Version");
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private PassiveXmlCaseSupport.Inspection noV1InV2Response(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var responses = document.getElementsByTagNameNS(PROTOCOL_2, "Response");
        for (var responseIndex = 0; responseIndex < responses.getLength(); responseIndex++) {
            var response = (Element) responses.item(responseIndex);
            if (!"2.0".equals(response.getAttribute("Version"))) continue;
            var descendants = response.getElementsByTagNameNS("*", "Assertion");
            for (var index = 0; index < descendants.getLength(); index++) {
                var assertion = (Element) descendants.item(index);
                observed++;
                var namespace = assertion.getNamespaceURI();
                var version = assertion.getAttribute("Version");
                if (ASSERTION_1.equals(namespace) || !version.startsWith("2.")) {
                    violations.add("{" + namespace + "}Assertion/@Version");
                }
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private PassiveXmlCaseSupport.Inspection authnRequestVersion(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var requests = document.getElementsByTagNameNS(PROTOCOL_2, "AuthnRequest");
        for (var index = 0; index < requests.getLength(); index++) {
            var request = (Element) requests.item(index);
            if (!request.hasAttribute("Version")) continue;
            observed++;
            if (!"2.0".equals(request.getAttribute("Version"))) {
                violations.add("{" + PROTOCOL_2 + "}AuthnRequest/@Version");
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }
}
