package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;

class IdpTimePrecisionScenarioTestCaseTest {
    @Test
    void comparesSubmillisecondProcessingWithASuccessfulControl() {
        var testCase = testCase();
        var control = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        var precise = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.resume(
                context(), control.next(), inbound(response(control.next(), true))));
        assertTrue(new String(precise.actions().getFirst().payload(), StandardCharsets.UTF_8)
                .contains(".000123456Z"));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), precise.next(), inbound(response(precise.next(), true))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void rejectingOnlyThePreciseTimestampIsAWarningClassViolationOutcome() {
        var testCase = testCase();
        var control = (CaseStep.AwaitInbound) testCase.start(context());
        var precise = (CaseStep.AwaitInbound) testCase.resume(
                context(), control.next(), inbound(response(control.next(), true)));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), precise.next(), inbound(response(precise.next(), false)));
        assertEquals(Outcome.VIOLATED, finish.outcome().outcome());
    }

    private IdpTimePrecisionScenarioTestCase testCase() {
        return new IdpTimePrecisionScenarioTestCase(ignored -> configuration());
    }

    private CaseEvent.InboundMessage inbound(String xml) {
        return new CaseEvent.InboundMessage(
                xml.getBytes(StandardCharsets.UTF_8), new EvidenceRef("transcript", "tx"));
    }

    private String response(CaseState state, boolean success) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" InResponseTo="%s">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:%s"/></samlp:Status>%s
                </samlp:Response>
                """.formatted(state.data().get("expected_response_correlation"),
                success ? "Success" : "Requester", success ? "<saml:Assertion/>" : "");
    }

    private IdpErrorProbeConfiguration configuration() {
        return new IdpErrorProbeConfiguration(
                URI.create("https://idp.example/sso"), "https://suite.example/sp",
                URI.create("https://suite.example/acs"), Duration.ofMinutes(2), true, true, true);
    }

    private CaseContext context() {
        return new CaseContext() {
            @Override public String runId() { return "run_0123456789ABCDEFGHJKMNPQRS"; }
            @Override public TargetRole targetRole() { return TargetRole.IDP; }
            @Override public Clock clock() {
                return Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC);
            }
            @Override public org.samlier.core.plan.TestPlan.Parameters parameters() { return null; }
            @Override public org.samlier.core.plan.TestPlan.Interaction interaction() {
                return org.samlier.core.plan.TestPlan.Interaction.defaults();
            }
            @Override public org.samlier.core.run.Reachability reachability() { return null; }
            @Override public org.samlier.core.transcript.TranscriptRecorder transcript() { return null; }
            @Override public boolean transcriptComplete() { return false; }
        };
    }
}
