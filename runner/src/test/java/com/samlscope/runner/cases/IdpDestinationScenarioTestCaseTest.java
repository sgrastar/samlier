package com.samlscope.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
import com.samlscope.saml.normal.SecureXml;

class IdpDestinationScenarioTestCaseTest {
    @Test
    void mismatchedDestinationsMustNotProduceSuccessfulAssertions() throws Exception {
        var testCase = testCase();
        var baseline = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        var mismatch = next(testCase, baseline, response(baseline.next(), true));
        var root = SecureXml.parse(mismatch.actions().getFirst().payload()).getDocumentElement();
        assertNotEquals(configuration().ssoEndpoint().toString(), root.getAttribute("Destination"));
        var next = next(testCase, mismatch, response(mismatch.next(), false));
        next = next(testCase, next, response(next.next(), false));
        var finish = assertInstanceOf(CaseStep.Finish.class,
                testCase.resume(context(), next.next(), inbound(response(next.next(), false))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void aSuccessfulAssertionForAMismatchedDestinationIsAViolation() {
        var testCase = testCase();
        var baseline = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        var mismatch = next(testCase, baseline, response(baseline.next(), true));
        var next = next(testCase, mismatch, response(mismatch.next(), true));
        next = next(testCase, next, response(next.next(), false));
        var finish = assertInstanceOf(CaseStep.Finish.class,
                testCase.resume(context(), next.next(), inbound(response(next.next(), false))));
        assertEquals(Outcome.VIOLATED, finish.outcome().outcome());
    }

    @Test
    void anExplicitTerminalErrorCountsAsDiscardForANegativeFixture() {
        var testCase = testCase();
        var baseline = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        var mismatch = next(testCase, baseline, response(baseline.next(), true));
        var next = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.resume(
                context(), mismatch.next(),
                new CaseEvent.InboundUnavailable("operator-reported-no-saml-response")));
        assertEquals("other-target-endpoint", next.next().data().get("fixture_id"));
    }

    private IdpDestinationScenarioTestCase testCase() {
        return new IdpDestinationScenarioTestCase(ignored -> configuration());
    }

    private CaseStep.AwaitInbound next(
            IdpDestinationScenarioTestCase testCase, CaseStep.AwaitInbound step, String response) {
        return assertInstanceOf(CaseStep.AwaitInbound.class,
                testCase.resume(context(), step.next(), inbound(response)));
    }

    private CaseEvent.InboundMessage inbound(String xml) {
        return new CaseEvent.InboundMessage(
                xml.getBytes(StandardCharsets.UTF_8), new EvidenceRef("transcript", "tx"));
    }

    private String response(CaseState state, boolean success) {
        var status = success ? "Success" : "Requester";
        var assertion = success ? "<saml:Assertion/>" : "";
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" Version="2.0"
                  InResponseTo="%s">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:%s"/></samlp:Status>%s
                </samlp:Response>
                """.formatted(state.data().get("expected_response_correlation"), status, assertion);
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
