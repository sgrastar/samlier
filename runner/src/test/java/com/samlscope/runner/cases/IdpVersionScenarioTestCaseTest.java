package com.samlscope.runner.cases;

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
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.evaluation.EvidenceRef;
import com.samlscope.core.evaluation.Outcome;
import com.samlscope.core.plan.TargetRole;

class IdpVersionScenarioTestCaseTest {
    @Test
    void exercisesBothUnsupportedMajorDirectionsAfterAWorkingControl() {
        var testCase = testCase();
        var control = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        assertTrue(xml(control).contains("Version=\"2.0\""));
        var old = next(testCase, control, response(control.next(), true));
        assertTrue(xml(old).contains("Version=\"1.1\""));
        var future = next(testCase, old, response(old.next(), false));
        assertTrue(xml(future).contains("Version=\"3.0\""));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), future.next(), inbound(response(future.next(), false))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void acceptingEitherUnsupportedMajorVersionIsAViolation() {
        var testCase = testCase();
        var control = (CaseStep.AwaitInbound) testCase.start(context());
        var old = next(testCase, control, response(control.next(), true));
        var future = next(testCase, old, response(old.next(), true));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), future.next(), inbound(response(future.next(), false)));
        assertEquals(Outcome.VIOLATED, finish.outcome().outcome());
    }

    private IdpVersionScenarioTestCase testCase() {
        return new IdpVersionScenarioTestCase(ignored -> configuration());
    }

    private CaseStep.AwaitInbound next(
            IdpVersionScenarioTestCase testCase, CaseStep.AwaitInbound step, String response) {
        return assertInstanceOf(CaseStep.AwaitInbound.class,
                testCase.resume(context(), step.next(), inbound(response)));
    }

    private CaseEvent.InboundMessage inbound(String xml) {
        return new CaseEvent.InboundMessage(
                xml.getBytes(StandardCharsets.UTF_8), new EvidenceRef("transcript", "tx"));
    }

    private String xml(CaseStep.AwaitInbound step) {
        return new String(step.actions().getFirst().payload(), StandardCharsets.UTF_8);
    }

    private String response(CaseState state, boolean success) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" Version="2.0" InResponseTo="%s">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:%s"/></samlp:Status>%s
                </samlp:Response>
                """.formatted(state.data().get("expected_response_correlation"),
                success ? "Success" : "VersionMismatch", success ? "<saml:Assertion/>" : "");
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
            @Override public com.samlscope.core.plan.TestPlan.Parameters parameters() { return null; }
            @Override public com.samlscope.core.plan.TestPlan.Interaction interaction() {
                return com.samlscope.core.plan.TestPlan.Interaction.defaults();
            }
            @Override public com.samlscope.core.run.Reachability reachability() { return null; }
            @Override public com.samlscope.core.transcript.TranscriptRecorder transcript() { return null; }
            @Override public boolean transcriptComplete() { return false; }
        };
    }
}
