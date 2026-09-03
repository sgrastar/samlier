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

class IdpAcsSelectionScenarioTestCaseTest {
    private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final URI ACS0 = URI.create("https://suite.example/sp/acs/0");
    private static final URI ACS1 = URI.create("https://suite.example/sp/acs/1");

    @Test
    void indexScenarioUsesDefaultControlThenNonDefaultIndex() {
        var testCase = testCase(IdpAcsSelectionScenarioTestCase.INDEX_CASE);
        var control = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        var controlXml = new String(control.actions().getFirst().payload(), StandardCharsets.UTF_8);
        assertTrue(!controlXml.contains("AssertionConsumerServiceIndex"));
        var selected = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.resume(
                context(), control.next(), inbound(control.next(), response(control.next(), "Success", ACS0))));
        var selectedXml = new String(selected.actions().getFirst().payload(), StandardCharsets.UTF_8);
        assertTrue(selectedXml.contains("AssertionConsumerServiceIndex=\"1\""));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), selected.next(), inbound(selected.next(), response(selected.next(), "Success", ACS1))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void fixedDefaultImplementationIsDetectedForUrlSelection() {
        var testCase = testCase(IdpAcsSelectionScenarioTestCase.URL_CASE);
        var control = (CaseStep.AwaitInbound) testCase.start(context());
        var selected = (CaseStep.AwaitInbound) testCase.resume(
                context(), control.next(), inbound(control.next(), response(control.next(), "Success", ACS0)));
        var selectedXml = new String(selected.actions().getFirst().payload(), StandardCharsets.UTF_8);
        assertTrue(selectedXml.contains("AssertionConsumerServiceURL=\"" + ACS1 + "\""));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), selected.next(), inbound(selected.next(), response(selected.next(), "Success", ACS0)));
        assertEquals(Outcome.VIOLATED, finish.outcome().outcome());
    }

    @Test
    void unsupportedBindingNeedsAnErrorRatherThanSilentPostFallback() {
        var testCase = testCase(IdpAcsSelectionScenarioTestCase.BINDING_CASE);
        var waiting = (CaseStep.AwaitInbound) testCase.start(context());
        assertTrue(new String(waiting.actions().getFirst().payload(), StandardCharsets.UTF_8)
                .contains("urn:samlscope:unsupported:response-binding"));
        var error = (CaseStep.Finish) testCase.resume(
                context(), waiting.next(), inbound(waiting.next(), response(waiting.next(), "Responder", ACS0)));
        assertEquals(Outcome.SATISFIED, error.outcome().outcome());

        var fallback = testCase(IdpAcsSelectionScenarioTestCase.BINDING_CASE);
        var second = (CaseStep.AwaitInbound) fallback.start(context());
        var inconclusive = (CaseStep.Finish) fallback.resume(
                context(), second.next(), inbound(second.next(), response(second.next(), "Success", ACS0)));
        assertEquals(Outcome.NOT_VERIFIED, inconclusive.outcome().outcome());
    }

    @Test
    void unknownIndexMayProduceAnErrorOrUseTheDefaultButNotAnotherEndpoint() {
        var errorCase = testCase(IdpAcsSelectionScenarioTestCase.UNKNOWN_INDEX_CASE);
        var errorWait = (CaseStep.AwaitInbound) errorCase.start(context());
        assertTrue(new String(errorWait.actions().getFirst().payload(), StandardCharsets.UTF_8)
                .contains("AssertionConsumerServiceIndex=\"999999\""));
        var error = (CaseStep.Finish) errorCase.resume(
                context(), errorWait.next(), inbound(
                        errorWait.next(), response(errorWait.next(), "Responder", ACS0)));
        assertEquals(Outcome.SATISFIED, error.outcome().outcome());

        var defaultCase = testCase(IdpAcsSelectionScenarioTestCase.UNKNOWN_INDEX_CASE);
        var defaultWait = (CaseStep.AwaitInbound) defaultCase.start(context());
        var defaultResult = (CaseStep.Finish) defaultCase.resume(
                context(), defaultWait.next(), inbound(
                        defaultWait.next(), response(defaultWait.next(), "Success", ACS0)));
        assertEquals(Outcome.SATISFIED, defaultResult.outcome().outcome());

        var wrongCase = testCase(IdpAcsSelectionScenarioTestCase.UNKNOWN_INDEX_CASE);
        var wrongWait = (CaseStep.AwaitInbound) wrongCase.start(context());
        var wrong = (CaseStep.Finish) wrongCase.resume(
                context(), wrongWait.next(), inbound(
                        wrongWait.next(), response(wrongWait.next(), "Success", ACS1)));
        assertEquals(Outcome.VIOLATED, wrong.outcome().outcome());
    }

    @Test
    void unregisteredUrlUsesARegisteredControlAndRejectsReturnToTheUnknownAcs() {
        var testCase = testCase(IdpAcsSelectionScenarioTestCase.UNREGISTERED_URL_CASE);
        var control = (CaseStep.AwaitInbound) testCase.start(context());
        var unknown = (CaseStep.AwaitInbound) testCase.resume(
                context(), control.next(), inbound(control.next(), response(control.next(), "Success", ACS1)));
        var unknownXml = new String(unknown.actions().getFirst().payload(), StandardCharsets.UTF_8);
        assertTrue(unknownXml.contains("AssertionConsumerServiceURL=\"https://suite.example/sp/acs/999999\""));
        var satisfied = (CaseStep.Finish) testCase.resume(
                context(), unknown.next(), inbound(unknown.next(), response(unknown.next(), "Responder", ACS0)));
        assertEquals(Outcome.SATISFIED, satisfied.outcome().outcome());

        var violatingCase = testCase(IdpAcsSelectionScenarioTestCase.UNREGISTERED_URL_CASE);
        var secondControl = (CaseStep.AwaitInbound) violatingCase.start(context());
        var secondUnknown = (CaseStep.AwaitInbound) violatingCase.resume(
                context(), secondControl.next(), inbound(
                        secondControl.next(), response(secondControl.next(), "Success", ACS1)));
        var violated = (CaseStep.Finish) violatingCase.resume(
                context(), secondUnknown.next(), inbound(secondUnknown.next(), response(
                        secondUnknown.next(), "Success", URI.create("https://suite.example/sp/acs/999999"))));
        assertEquals(Outcome.VIOLATED, violated.outcome().outcome());
    }

    private IdpAcsSelectionScenarioTestCase testCase(String id) {
        return new IdpAcsSelectionScenarioTestCase(id, ignored -> configuration());
    }

    private CaseEvent.InboundMessage inbound(CaseState state, String xml) {
        return new CaseEvent.InboundMessage(
                xml.getBytes(StandardCharsets.UTF_8),
                new EvidenceRef("transcript", "tx-" + state.data().get("fixture_id")));
    }

    private String response(CaseState state, String status, URI destination) {
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  InResponseTo="%s" Destination="%s">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:%s"/></samlp:Status>
                </samlp:Response>
                """.formatted(state.data().get("expected_response_correlation"), destination, status);
    }

    private IdpErrorProbeConfiguration configuration() {
        return new IdpErrorProbeConfiguration(
                URI.create("https://idp.example/sso"), "https://suite.example/sp", ACS0,
                Duration.ofMinutes(2), true, true, true);
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
