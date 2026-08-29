package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;

class SamlTimeRelationshipCaseTest {
    @Test
    void conditionsRequireStrictlyIncreasingBoundsAfterTimezoneNormalization() {
        assertOutcome(SamlTimeRelationshipCase.Rule.CONDITIONS_ORDER, Outcome.SATISFIED, """
                <saml:Conditions xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  NotBefore="2026-08-29T09:00:00+09:00" NotOnOrAfter="2026-08-29T00:05:00Z"/>
                """);
        assertOutcome(SamlTimeRelationshipCase.Rule.CONDITIONS_ORDER, Outcome.VIOLATED, """
                <saml:Conditions xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  NotBefore="2026-08-29T00:05:00Z" NotOnOrAfter="2026-08-29T00:05:00Z"/>
                """);
    }

    @Test
    void confirmationPeriodMustBeContainedAtBothEnds() {
        assertOutcome(SamlTimeRelationshipCase.Rule.CONFIRMATION_WITHIN_CONDITIONS, Outcome.SATISFIED, assertion(
                "2026-08-29T00:00:00Z", "2026-08-29T00:10:00Z",
                "2026-08-29T00:01:00Z", "2026-08-29T00:09:00Z"));
        assertOutcome(SamlTimeRelationshipCase.Rule.CONFIRMATION_WITHIN_CONDITIONS, Outcome.VIOLATED, assertion(
                "2026-08-29T00:00:00Z", "2026-08-29T00:10:00Z",
                "2026-08-28T23:59:00Z", "2026-08-29T00:11:00Z"));
    }

    @Test
    void confirmationOwnBoundsRequireStrictOrder() {
        assertOutcome(SamlTimeRelationshipCase.Rule.CONFIRMATION_ORDER, Outcome.VIOLATED, assertion(
                null, null, "2026-08-29T00:05:00Z", "2026-08-29T00:04:00Z"));
    }

    @Test
    void invalidOrIndeterminateTimesAreNotTargetViolations() {
        assertOutcome(SamlTimeRelationshipCase.Rule.CONDITIONS_ORDER, Outcome.NOT_VERIFIED, """
                <saml:Conditions xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  NotBefore="not-a-date" NotOnOrAfter="2026-08-29T00:05:00Z"/>
                """);
        assertOutcome(SamlTimeRelationshipCase.Rule.CONDITIONS_ORDER, Outcome.NOT_VERIFIED, """
                <saml:Conditions xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                  NotBefore="2026-08-29T00:00:00" NotOnOrAfter="2026-08-29T00:05:00Z"/>
                """);
    }

    private String assertion(String cStart, String cEnd, String sStart, String sEnd) {
        return """
                <saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion">
                  <saml:Subject><saml:SubjectConfirmation><saml:SubjectConfirmationData%s%s/>
                  </saml:SubjectConfirmation></saml:Subject>
                  <saml:Conditions%s%s/>
                </saml:Assertion>
                """.formatted(attr("NotBefore", sStart), attr("NotOnOrAfter", sEnd),
                attr("NotBefore", cStart), attr("NotOnOrAfter", cEnd));
    }

    private String attr(String name, String value) {
        return value == null ? "" : " " + name + "=\"" + value + "\"";
    }

    private void assertOutcome(SamlTimeRelationshipCase.Rule rule, Outcome expected, String xml) {
        var outcome = new SamlTimeRelationshipCase(rule).evaluate(List.of(
                new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8))));
        assertEquals(expected, outcome.outcome(), rule.name());
    }
}
