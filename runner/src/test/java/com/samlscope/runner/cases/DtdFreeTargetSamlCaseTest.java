package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.Outcome;

class DtdFreeTargetSamlCaseTest {
    private final DtdFreeTargetSamlCase testCase = new DtdFreeTargetSamlCase();

    @Test
    void satisfiesTheObligationWhenEveryObservedMessageIsDtdFree() {
        var outcome = testCase.evaluate(List.of(
                message("transcript:request", "<samlp:AuthnRequest xmlns:samlp='urn:oasis:names:tc:SAML:2.0:protocol'/>"),
                message("transcript:response", "<samlp:Response xmlns:samlp='urn:oasis:names:tc:SAML:2.0:protocol'/>")
        ));

        assertEquals(Outcome.SATISFIED, outcome.outcome());
        assertEquals(2, outcome.details().get("inspected_messages"));
    }

    @Test
    void violatesTheObligationWhenAnyTargetMessageContainsADtd() {
        var outcome = testCase.evaluate(List.of(
                message("transcript:good", "<root/>"),
                message("transcript:bad", "<!DOCTYPE root [<!ELEMENT root EMPTY>]><root/>")));

        assertEquals(Outcome.VIOLATED, outcome.outcome());
        assertEquals("transcript:bad", outcome.evidence().getFirst().reference());
    }

    @Test
    void noObservationIsNotAFalsePass() {
        var outcome = testCase.evaluate(List.of());

        assertEquals(Outcome.NOT_VERIFIED, outcome.outcome());
        assertEquals("no_target_generated_saml_messages", outcome.notVerifiedReason());
    }

    private static TargetTranscriptMessages.Message message(String ref, String xml) {
        return new TargetTranscriptMessages.Message(ref, xml.getBytes(StandardCharsets.UTF_8));
    }
}
