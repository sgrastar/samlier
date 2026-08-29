package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import org.samlier.core.evaluation.CaseOutcome;
import org.w3c.dom.Element;

/** Passive requester-side checks for the AllowCreate rules introduced by SAML errata E14. */
public final class SamlAllowCreateCase {
    public enum Rule { GENERAL_INTEROPERABILITY, TRANSIENT_ABSENT }

    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String TRANSIENT = "urn:oasis:names:tc:SAML:2.0:nameid-format:transient";
    private final Rule rule;

    public SamlAllowCreateCase(Rule rule) {
        this.rule = java.util.Objects.requireNonNull(rule, "rule");
    }

    public CaseOutcome evaluate(List<TargetTranscriptMessages.Message> messages) {
        var code = "saml.allow-create." + rule.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        return PassiveXmlCaseSupport.evaluate(messages, code, this::inspect);
    }

    private PassiveXmlCaseSupport.Inspection inspect(org.w3c.dom.Document document) {
        var observed = 0;
        var violations = new ArrayList<String>();
        var policies = document.getElementsByTagNameNS(PROTOCOL, "NameIDPolicy");
        for (var index = 0; index < policies.getLength(); index++) {
            var policy = (Element) policies.item(index);
            var transientRequest = TRANSIENT.equals(policy.getAttribute("Format"));
            if (rule == Rule.GENERAL_INTEROPERABILITY && !transientRequest) {
                observed++;
                if (!isTrue(policy.getAttribute("AllowCreate"))) {
                    violations.add("{" + PROTOCOL + "}NameIDPolicy/@AllowCreate");
                }
            } else if (rule == Rule.TRANSIENT_ABSENT && transientRequest) {
                observed++;
                if (policy.hasAttribute("AllowCreate")) {
                    violations.add("{" + PROTOCOL + "}NameIDPolicy/@AllowCreate");
                }
            }
        }
        return new PassiveXmlCaseSupport.Inspection(observed, violations);
    }

    private boolean isTrue(String value) {
        return "true".equals(value) || "1".equals(value);
    }
}
