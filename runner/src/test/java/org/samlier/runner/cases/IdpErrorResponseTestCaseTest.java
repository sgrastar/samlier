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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.ActionIds;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;

class IdpErrorResponseTestCaseTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";
    private final IdpErrorResponseTestCase testCase = new IdpErrorResponseTestCase(configuration(true, true, true));

    @Test
    void runsAllThreeProbesThroughDeterministicOutboxActions() {
        var first = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        assertAction(first, "await-unknown-nameid-format");
        assertTrue(new String(first.actions().get(0).payload(), StandardCharsets.UTF_8).contains("NameIDPolicy"));

        var second = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.resume(
                context(), first.next(), inbound(error(first.next(), "Responder", null), "unknown-format")));
        assertAction(second, "await-unsatisfiable-authn-context");
        assertTrue(new String(second.actions().get(0).payload(), StandardCharsets.UTF_8).contains("RequestedAuthnContext"));

        var third = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.resume(
                context(), second.next(), inbound(error(second.next(), "Responder", null), "authn-context")));
        assertAction(third, "await-passive-without-session");
        assertTrue(new String(third.actions().get(0).payload(), StandardCharsets.UTF_8).contains("IsPassive=\"true\""));

        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), third.next(), inbound(error(third.next(), "Requester", null), "passive")));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
        assertEquals(3, finish.outcome().evidence().size());
    }

    @Test
    void aSuccessResponseForAnErrorProbeProducesViolatedOnlyAfterAllControlsRun() {
        var first = (CaseStep.AwaitInbound) testCase.start(context());
        var second = (CaseStep.AwaitInbound) testCase.resume(
                context(), first.next(), inbound(error(first.next(), "Success", null), "unexpected-success"));
        var third = (CaseStep.AwaitInbound) testCase.resume(
                context(), second.next(), inbound(error(second.next(), "Responder", null), "expected-error"));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), third.next(), inbound(error(third.next(), "Responder", null), "expected-passive-error"));

        assertEquals(Outcome.VIOLATED, finish.outcome().outcome());
    }

    @Test
    void aTargetThatActuallyReturnsTheProbeAuthnContextDoesNotGetAFalseViolation() {
        var first = (CaseStep.AwaitInbound) testCase.start(context());
        var second = (CaseStep.AwaitInbound) testCase.resume(
                context(), first.next(), inbound(error(first.next(), "Responder", null), "first"));
        var requestId = (String) second.next().data().get("request_id");
        var requested = new org.samlier.saml.normal.SamlErrorProbeRequestFactory().unavailableAuthnContext(requestId);
        var third = (CaseStep.AwaitInbound) testCase.resume(
                context(), second.next(), inbound(error(second.next(), "Success", requested), "supported-context"));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), third.next(), inbound(error(third.next(), "Responder", null), "third"));

        assertEquals(Outcome.NOT_VERIFIED, finish.outcome().outcome());
    }

    @Test
    void timeoutAndMissingPreconditionsNeverBecomeTargetViolations() {
        var waiting = (CaseStep.AwaitInbound) testCase.start(context());
        var timeout = (CaseStep.Finish) testCase.resume(
                context(), waiting.next(), new CaseEvent.TimedOut(Duration.ofMinutes(2)));
        var unavailable = (CaseStep.Finish) new IdpErrorResponseTestCase(configuration(true, true, false)).start(context());

        assertEquals(Outcome.NOT_VERIFIED, timeout.outcome().outcome());
        assertEquals(Outcome.NOT_VERIFIED, unavailable.outcome().outcome());
    }

    private void assertAction(CaseStep.AwaitInbound step, String phase) {
        assertEquals(phase, step.next().phase());
        assertEquals(ActionIds.derive(RUN_ID, testCase.id(), phase, 0), step.actions().get(0).actionId());
        assertEquals(step.next().data().get("request_id"), step.matcher().criteria().get("InResponseTo"));
    }

    private CaseEvent.InboundMessage inbound(String xml, String evidence) {
        return new CaseEvent.InboundMessage(
                xml.getBytes(StandardCharsets.UTF_8), new EvidenceRef("transcript", evidence));
    }

    private String error(CaseState state, String status, String authnContext) {
        var context = authnContext == null ? "" : """
                <saml:Assertion><saml:AuthnStatement><saml:AuthnContext>
                  <saml:AuthnContextClassRef>%s</saml:AuthnContextClassRef>
                </saml:AuthnContext></saml:AuthnStatement></saml:Assertion>
                """.formatted(authnContext);
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" InResponseTo="%s">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:%s"/></samlp:Status>%s
                </samlp:Response>
                """.formatted(state.data().get("request_id"), status, context);
    }

    private IdpErrorProbeConfiguration configuration(boolean agent, boolean location, boolean noSession) {
        return new IdpErrorProbeConfiguration(
                URI.create("https://idp.example/sso"), "https://suite.example/sp",
                URI.create("https://suite.example/acs"), Duration.ofMinutes(2), agent, location, noSession);
    }

    private CaseContext context() {
        return new CaseContext() {
            @Override public String runId() { return RUN_ID; }
            @Override public TargetRole targetRole() { return TargetRole.IDP; }
            @Override public Clock clock() { return Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC); }
            @Override public org.samlier.core.plan.TestPlan.Parameters parameters() { return null; }
            @Override public org.samlier.core.run.Reachability reachability() { return null; }
            @Override public org.samlier.core.transcript.TranscriptRecorder transcript() { return null; }
            @Override public boolean transcriptComplete() { return false; }
        };
    }
}
