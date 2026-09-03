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

class IdpUnknownExtensionScenarioTestCaseTest {
    private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";

    @Test
    void comparesAnUnknownExtensionFlowWithAnOrdinaryLoginControl() {
        var testCase = testCase();
        var control = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        var extension = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.resume(
                context(), control.next(), inbound(response(control.next(), true, true))));
        var request = new String(extension.actions().getFirst().payload(), StandardCharsets.UTF_8);
        assertTrue(request.contains("samlp:Extensions"));
        assertTrue(request.contains("urn:samlscope:probe:unknown-extension"));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), extension.next(), inbound(response(extension.next(), true, true))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void rejectingOnlyTheWellFormedUnknownExtensionIsAViolation() {
        var testCase = testCase();
        var control = (CaseStep.AwaitInbound) testCase.start(context());
        var extension = (CaseStep.AwaitInbound) testCase.resume(
                context(), control.next(), inbound(response(control.next(), true, true)));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), extension.next(), inbound(response(extension.next(), false, false)));
        assertEquals(Outcome.VIOLATED, finish.outcome().outcome());
    }

    private IdpUnknownExtensionScenarioTestCase testCase() {
        return new IdpUnknownExtensionScenarioTestCase(ignored -> configuration());
    }

    private CaseEvent.InboundMessage inbound(String xml) {
        return new CaseEvent.InboundMessage(
                xml.getBytes(StandardCharsets.UTF_8), new EvidenceRef("transcript", "tx"));
    }

    private String response(CaseState state, boolean success, boolean assertion) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" InResponseTo="%s">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:%s"/></samlp:Status>%s
                </samlp:Response>
                """.formatted(state.data().get("expected_response_correlation"),
                success ? "Success" : "Responder", assertion ? "<saml:Assertion/>" : "");
    }

    private IdpErrorProbeConfiguration configuration() {
        return new IdpErrorProbeConfiguration(
                URI.create("https://idp.example/sso"), "https://suite.example/sp",
                URI.create("https://suite.example/acs"), Duration.ofMinutes(2), true, true, true);
    }

    private CaseContext context() {
        return new CaseContext() {
            @Override public String runId() { return RUN; }
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
