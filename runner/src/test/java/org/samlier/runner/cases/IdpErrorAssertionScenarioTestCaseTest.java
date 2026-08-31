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

class IdpErrorAssertionScenarioTestCaseTest {
    private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";
    private final IdpErrorAssertionScenarioTestCase testCase =
            new IdpErrorAssertionScenarioTestCase(ignored -> configuration());

    @Test
    void runsPositiveAndThreeDifferentErrorPathsWithoutAnOperatorVerdict() {
        var baseline = assertInstanceOf(CaseStep.AwaitInbound.class, testCase.start(context()));
        var unknownFormat = next(baseline, response(baseline.next(), true, true, false));
        assertTrue(xml(unknownFormat).contains("NameIDPolicy"));
        var unknownSubject = next(unknownFormat, response(unknownFormat.next(), false, false, false));
        assertTrue(xml(unknownSubject).contains("urn:samlier:probe:unknown-subject"));
        var passive = next(unknownSubject, response(unknownSubject.next(), false, false, false));
        assertTrue(xml(passive).contains("IsPassive=\"true\""));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), passive.next(), inbound(response(passive.next(), false, false, false))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void anAssertionInsideAnyErrorResponseIsAViolation() {
        var baseline = (CaseStep.AwaitInbound) testCase.start(context());
        var unknownFormat = next(baseline, response(baseline.next(), true, true, false));
        var unknownSubject = next(unknownFormat, response(unknownFormat.next(), false, true, false));
        var passive = next(unknownSubject, response(unknownSubject.next(), false, false, false));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), passive.next(), inbound(response(passive.next(), false, false, false)));
        assertEquals(Outcome.VIOLATED, finish.outcome().outcome());
    }

    @Test
    void encryptedAssertionsAreAlsoForbiddenOnError() {
        var baseline = (CaseStep.AwaitInbound) testCase.start(context());
        var unknownFormat = next(baseline, response(baseline.next(), true, true, false));
        var unknownSubject = next(unknownFormat, response(unknownFormat.next(), false, false, true));
        var passive = next(unknownSubject, response(unknownSubject.next(), false, false, false));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), passive.next(), inbound(response(passive.next(), false, false, false)));
        assertEquals(Outcome.VIOLATED, finish.outcome().outcome());
    }

    @Test
    void subjectSpecificObligationUsesAControlAndTheUnrecognizedSubjectOnly() {
        var subjectCase = new IdpErrorAssertionScenarioTestCase(
                IdpErrorAssertionScenarioTestCase.SUBJECT_ERROR_CASE, ignored -> configuration());
        var baseline = assertInstanceOf(CaseStep.AwaitInbound.class, subjectCase.start(context()));
        var subject = assertInstanceOf(CaseStep.AwaitInbound.class, subjectCase.resume(
                context(), baseline.next(), inbound(response(baseline.next(), true, true, false))));
        assertTrue(xml(subject).contains("urn:samlier:probe:unknown-subject"));
        var finish = assertInstanceOf(CaseStep.Finish.class, subjectCase.resume(
                context(), subject.next(), inbound(response(subject.next(), false, false, false))));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    private CaseStep.AwaitInbound next(CaseStep.AwaitInbound step, String response) {
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

    private String response(CaseState state, boolean success, boolean assertion, boolean encrypted) {
        var content = assertion ? "<saml:Assertion/>" : encrypted ? "<saml:EncryptedAssertion/>" : "";
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" InResponseTo="%s">
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:%s"/></samlp:Status>%s
                </samlp:Response>
                """.formatted(state.data().get("expected_response_correlation"),
                success ? "Success" : "Responder", content);
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
