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

class IdpPassiveScenarioTestCaseTest {
    private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";

    @Test
    void passiveScenarioExercisesAbsentAndExistingSessionWithoutAskingForAVerdict() {
        var testCase = testCase(IdpPassiveScenarioTestCase.PASSIVE_CASE);
        var noSession = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        assertTrue(xml(noSession).contains("IsPassive=\"true\""));
        assertTrue(testCase.requiresFreshSession(noSession.next()));

        var establish = next(testCase, noSession, response(noSession.next(), false, false));
        assertTrue(!xml(establish).contains("IsPassive=\"true\""));
        assertTrue(!testCase.requiresFreshSession(establish.next()));

        var withSession = next(testCase, establish, response(establish.next(), true, true));
        assertTrue(xml(withSession).contains("IsPassive=\"true\""));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), withSession.next(), inbound(response(withSession.next(), true, true))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void passiveSuccessWithoutAnAssertionIsNotClaimedAsConforming() {
        var testCase = testCase(IdpPassiveScenarioTestCase.PASSIVE_CASE);
        var noSession = (CaseStep.AwaitInbound) testCase.start(context());
        var establish = next(testCase, noSession, response(noSession.next(), true, false));
        var withSession = next(testCase, establish, response(establish.next(), true, true));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), withSession.next(), inbound(response(withSession.next(), true, true)));
        assertEquals(Outcome.NOT_VERIFIED, finish.outcome().outcome());
    }

    @Test
    void forceAuthnAndPassiveErrorIsConclusiveButSuccessIsNot() {
        var testCase = testCase(IdpPassiveScenarioTestCase.FORCE_PASSIVE_CASE);
        var waiting = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        assertTrue(xml(waiting).contains("ForceAuthn=\"true\""));
        assertTrue(xml(waiting).contains("IsPassive=\"true\""));
        assertTrue(testCase.requiresFreshSession(waiting.next()));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), waiting.next(), inbound(response(waiting.next(), false, false))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());

        var successCase = testCase(IdpPassiveScenarioTestCase.FORCE_PASSIVE_CASE);
        var second = (CaseStep.AwaitInbound) successCase.start(context());
        var inconclusive = (CaseStep.Finish) successCase.resume(
                context(), second.next(), inbound(response(second.next(), true, true)));
        assertEquals(Outcome.NOT_VERIFIED, inconclusive.outcome().outcome());
    }

    private IdpPassiveScenarioTestCase testCase(String id) {
        return new IdpPassiveScenarioTestCase(id, ignored -> configuration());
    }

    private CaseStep.AwaitInbound next(
            IdpPassiveScenarioTestCase testCase, CaseStep.AwaitInbound step, String response) {
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
