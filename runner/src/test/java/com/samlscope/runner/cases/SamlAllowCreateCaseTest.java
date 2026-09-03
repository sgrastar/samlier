package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class SamlAllowCreateCaseTest {
    @Test
    void generalRuleRequiresTrueOnlyForNonTransientRequests() {
        assertOutcome(SamlAllowCreateCase.Rule.GENERAL_INTEROPERABILITY, Outcome.SATISFIED,
                policy("urn:example:persistent", "true"));
        assertOutcome(SamlAllowCreateCase.Rule.GENERAL_INTEROPERABILITY, Outcome.SATISFIED,
                policy(null, "1"));
        assertOutcome(SamlAllowCreateCase.Rule.GENERAL_INTEROPERABILITY, Outcome.VIOLATED,
                policy("urn:example:persistent", null));
        assertOutcome(SamlAllowCreateCase.Rule.GENERAL_INTEROPERABILITY, Outcome.SATISFIED_WITH_NOTE,
                policy("urn:oasis:names:tc:SAML:2.0:nameid-format:transient", null));
    }

    @Test
    void transientRuleProhibitsTheAttributeRegardlessOfItsValue() {
        assertOutcome(SamlAllowCreateCase.Rule.TRANSIENT_ABSENT, Outcome.SATISFIED,
                policy("urn:oasis:names:tc:SAML:2.0:nameid-format:transient", null));
        assertOutcome(SamlAllowCreateCase.Rule.TRANSIENT_ABSENT, Outcome.VIOLATED,
                policy("urn:oasis:names:tc:SAML:2.0:nameid-format:transient", "false"));
        assertOutcome(SamlAllowCreateCase.Rule.TRANSIENT_ABSENT, Outcome.SATISFIED_WITH_NOTE,
                policy(null, "true"));
    }

    @Test
    void returnedTransientAssertionsDoNotRetroactivelyChangeRequesterOutcome() {
        assertOutcome(SamlAllowCreateCase.Rule.TRANSIENT_ABSENT, Outcome.SATISFIED_WITH_NOTE, """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:Subject><saml:NameID Format="urn:oasis:names:tc:SAML:2.0:nameid-format:transient">id</saml:NameID></saml:Subject>
                </saml:Assertion>
                """);
    }

    private String policy(String format, String allowCreate) {
        return "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\"><samlp:NameIDPolicy"
                + attr("Format", format) + attr("AllowCreate", allowCreate) + "/></samlp:AuthnRequest>";
    }

    private String attr(String name, String value) {
        return value == null ? "" : " " + name + "=\"" + value + "\"";
    }

    private void assertOutcome(SamlAllowCreateCase.Rule rule, Outcome expected, String xml) {
        var outcome = new SamlAllowCreateCase(rule).evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome(), rule.name());
    }
}
