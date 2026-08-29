package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.DefaultCaseContext;

class BrowserEvidenceTestCaseTest {
    private static final Instant NOW = Instant.parse("2026-08-29T13:00:00Z");
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";

    @Test
    void requiresBrowserCompletionBeforeAcceptingARestrictedEvidenceAnswer() {
        var evidence = new AttestedOutcomeTestCase(
                "IIP-G01-a-idp-01", TargetRole.IDP, "browser.evidence", "Review browser evidence.",
                Duration.ofHours(1), List.of(
                        AttestationOption.of("satisfied", Outcome.SATISFIED, "satisfied"),
                        AttestationOption.notVerified("unclear", "unclear", "evidence_unavailable")));
        var testCase = new BrowserEvidenceTestCase(
                evidence, URI.create("https://suite.example"), "Execute both controls.", Duration.ofHours(1));

        var browser = assertInstanceOf(CaseStep.AwaitBrowser.class, testCase.start(context()));
        assertEquals(URI.create("https://suite.example/browser/" + RUN_ID + "/IIP-G01-a-idp-01"),
                browser.startUrl());
        assertThrows(IllegalArgumentException.class, () -> testCase.resume(
                context(), browser.next(), new CaseEvent.Attested("satisfied", "too early")));

        var attestation = assertInstanceOf(CaseStep.AwaitAttestation.class, testCase.resume(
                context(), browser.next(), new CaseEvent.BrowserReturned("operator-completed-approved-steps")));
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.resume(
                context(), attestation.next(), new CaseEvent.Attested("satisfied", "Transcript entry 12")));
        assertEquals(Outcome.SATISFIED, finish.outcome().outcome());
        assertThrows(IllegalArgumentException.class, () -> testCase.resume(
                context(), attestation.next(), new CaseEvent.Attested("PASS", "client verdict")));
    }

    private DefaultCaseContext context() {
        var plan = new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Browser", PlanProfile.IDP_CORE,
                new TestPlan.Target(
                        TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                new TestPlan.Interaction(true, true), NOW, NOW);
        return new DefaultCaseContext(
                RUN_ID, TargetRole.IDP, Clock.fixed(NOW, ZoneOffset.UTC), plan.parameters(), plan.interaction(),
                Reachability.CONFIRMED, new NoopTranscript(), true);
    }

    private static final class NoopTranscript implements TranscriptRecorder {
        @Override public TranscriptEntry record(TranscriptInput input) { throw new UnsupportedOperationException(); }
        @Override public TranscriptEntry updateSamlAnalysis(
                String entryId, String correlationId, Map<String, Object> summary) {
            throw new UnsupportedOperationException();
        }
        @Override public List<TranscriptEntry> list(String runId) { return List.of(); }
    }
}
