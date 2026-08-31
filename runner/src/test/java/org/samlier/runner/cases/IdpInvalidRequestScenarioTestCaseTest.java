package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

class IdpInvalidRequestScenarioTestCaseTest {
    @Test
    void invalidRequestsUseRequesterWhenTheIdpReturnsSaml() {
        var testCase = testCase(IdpInvalidRequestScenarioTestCase.STATUS_CASE);
        var baseline = (CaseStep.AwaitInbound) testCase.start(context());
        var missing = next(testCase, baseline, response(baseline.next(), "Success", true));
        assertFalse(xml(missing).contains(" ID=\""));
        var version = next(testCase, missing, response(missing.next(), "Requester", false));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), version.next(), inbound(response(version.next(), "Requester", true)));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void missingRequestIdRequiresAbsentInResponseToWhenResponseExists() {
        var testCase = testCase(IdpInvalidRequestScenarioTestCase.CORRELATION_CASE);
        var baseline = (CaseStep.AwaitInbound) testCase.start(context());
        var missing = next(testCase, baseline, response(baseline.next(), "Success", true));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), missing.next(), inbound(response(missing.next(), "Requester", false)));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    @Test
    void anInResponseToForAnIdLessRequestIsAViolation() {
        var testCase = testCase(IdpInvalidRequestScenarioTestCase.CORRELATION_CASE);
        var baseline = (CaseStep.AwaitInbound) testCase.start(context());
        var missing = next(testCase, baseline, response(baseline.next(), "Success", true));
        var finish = (CaseStep.Finish) testCase.resume(
                context(), missing.next(), inbound(response(missing.next(), "Requester", true)));
        assertEquals(Outcome.VIOLATED, finish.outcome().outcome());
    }

    @Test
    void aTerminalHttpErrorDoesNotViolateTheConditionalCoreRules() {
        var testCase = testCase(IdpInvalidRequestScenarioTestCase.CORRELATION_CASE);
        var baseline = (CaseStep.AwaitInbound) testCase.start(context());
        var missing = next(testCase, baseline, response(baseline.next(), "Success", true));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), missing.next(),
                new CaseEvent.InboundUnavailable("operator-reported-no-saml-response")));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
    }

    private IdpInvalidRequestScenarioTestCase testCase(String id) {
        return new IdpInvalidRequestScenarioTestCase(id, ignored -> configuration());
    }

    private CaseStep.AwaitInbound next(
            IdpInvalidRequestScenarioTestCase testCase, CaseStep.AwaitInbound step, String response) {
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

    private String response(CaseState state, String status, boolean includeCorrelation) {
        var correlation = includeCorrelation
                ? " InResponseTo=\"" + state.data().get("expected_response_correlation") + "\"" : "";
        var assertion = "Success".equals(status) ? "<saml:Assertion/>" : "";
        return """
                <samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" Version="2.0"%s>
                  <samlp:Status><samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:%s"/></samlp:Status>%s
                </samlp:Response>
                """.formatted(correlation, status, assertion);
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
