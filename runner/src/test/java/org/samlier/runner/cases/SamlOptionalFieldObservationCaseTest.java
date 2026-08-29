package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.Outcome;

class SamlOptionalFieldObservationCaseTest {
    private static final String PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";

    @Test
    void presenceAndAbsenceAreBothInformationallySatisfied() {
        var selector = SamlOptionalFieldObservationCase.Selector.attribute(
                new QName(PROTOCOL, "AuthnRequest"), new QName("", "ForceAuthn"));
        var oracle = new SamlOptionalFieldObservationCase(selector);
        var present = oracle.evaluate(List.of(message(request(" ForceAuthn=\"true\""))));
        var absent = oracle.evaluate(List.of(message(request(""))));
        assertEquals(Outcome.SATISFIED, present.outcome());
        assertEquals(true, present.details().get("present"));
        assertEquals(Outcome.SATISFIED, absent.outcome());
        assertEquals(false, absent.details().get("present"));
    }

    @Test
    void optionalElementSelectionRecordsEveryOccurrenceWithoutCreatingAViolation() {
        var selector = SamlOptionalFieldObservationCase.Selector.element(new QName(PROTOCOL, "Scoping"));
        var outcome = new SamlOptionalFieldObservationCase(selector).evaluate(List.of(message("""
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol">
                  <samlp:Scoping/><samlp:Scoping/>
                </samlp:AuthnRequest>
                """)));
        assertEquals(Outcome.SATISFIED, outcome.outcome());
        assertEquals(2, outcome.details().get("observed_fields"));
    }

    @Test
    void malformedTargetMessageIsNotReportedAsOptionalFeatureAbsence() {
        var selector = SamlOptionalFieldObservationCase.Selector.element(new QName(PROTOCOL, "Scoping"));
        var outcome = new SamlOptionalFieldObservationCase(selector).evaluate(List.of(message("<broken")));
        assertEquals(Outcome.NOT_VERIFIED, outcome.outcome());
    }

    private String request(String attributes) {
        return "<samlp:AuthnRequest xmlns:samlp=\"" + PROTOCOL + "\"" + attributes + "/>";
    }

    private TargetTranscriptMessages.Message message(String xml) {
        return new TargetTranscriptMessages.Message("message", xml.getBytes(StandardCharsets.UTF_8));
    }
}
