package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.ConfigurationFailureSemantics;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.DefaultCaseContext;

class MetadataFixtureObservationTestCaseTest {
    private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void requiresWorkingControlAndAllFixtureFetchesBeforeItIsReady() {
        var testCase = testCase();
        var incomplete = testCase.evidenceStatus(context(List.of(fetch("control", 1), use("control", 2))));
        assertEquals(false, incomplete.ready());

        var ready = testCase.evidenceStatus(context(List.of(
                fetch("control", 1), use("control", 2),
                fetch("accepted", 3), use("accepted", 4), fetch("rejected", 5))));
        assertEquals(true, ready.ready());
        assertEquals(4, ready.requiredObservations().size());
        assertEquals(4, ready.completedObservations().size());
    }

    @Test
    void judgesAcceptAndRejectFixturesWithoutUsingMissingObservationAsTargetFailure() {
        var testCase = testCase();
        assertEquals(Outcome.SATISFIED, evaluate(testCase, List.of(
                fetch("control", 1), use("control", 2),
                fetch("accepted", 3), use("accepted", 4), fetch("rejected", 5))));
        assertEquals(Outcome.VIOLATED, evaluate(testCase, List.of(
                fetch("control", 1), use("control", 2),
                fetch("accepted", 3), fetch("rejected", 4), use("rejected", 5))));
        assertEquals(Outcome.NOT_VERIFIED, evaluate(testCase, List.of(
                fetch("control", 1), use("control", 2), fetch("accepted", 3))));
    }

    @Test
    void preservesTheApprovedConfigurationFailureSemantics() {
        var context = context(List.of());
        var normative = testCase(ConfigurationFailureSemantics.NORMATIVE_CAPABILITY);
        var normativeStart = (CaseStep.AwaitConfig) normative.start(context);
        var absent = (CaseStep.Finish) normative.resume(
                context, normativeStart.next(),
                new CaseEvent.ConfigUnavailable(CaseEvent.ConfigurationIssue.CAPABILITY_ABSENT, "missing"));
        assertEquals(Outcome.VIOLATED, absent.outcome().outcome());

        var precondition = testCase(ConfigurationFailureSemantics.TEST_PRECONDITION);
        var preconditionStart = (CaseStep.AwaitConfig) precondition.start(context);
        var unavailable = (CaseStep.Finish) precondition.resume(
                context, preconditionStart.next(),
                new CaseEvent.ConfigUnavailable(CaseEvent.ConfigurationIssue.CAPABILITY_ABSENT, "missing"));
        assertEquals(Outcome.NOT_VERIFIED, unavailable.outcome().outcome());
    }

    private MetadataFixtureObservationTestCase testCase() {
        return testCase(ConfigurationFailureSemantics.TEST_PRECONDITION);
    }

    private MetadataFixtureObservationTestCase testCase(ConfigurationFailureSemantics semantics) {
        return new MetadataFixtureObservationTestCase("case", TargetRole.IDP, List.of(
                new MetadataFixtureObservationTestCase.Fixture(
                        "accepted", MetadataFixtureObservationTestCase.Behavior.ACCEPT, "positive"),
                new MetadataFixtureObservationTestCase.Fixture(
                        "rejected", MetadataFixtureObservationTestCase.Behavior.REJECT, "negative")),
                semantics);
    }

    private Outcome evaluate(MetadataFixtureObservationTestCase testCase, List<TranscriptEntry> entries) {
        var context = context(entries);
        var start = (CaseStep.AwaitConfig) testCase.start(context);
        return ((CaseStep.Finish) testCase.resume(
                context, start.next(), new CaseEvent.ConfigConfirmed())).outcome().outcome();
    }

    private CaseContext context(List<TranscriptEntry> entries) {
        return new DefaultCaseContext(
                RUN, TargetRole.IDP, Clock.fixed(NOW, ZoneOffset.UTC),
                org.samlier.core.plan.TestPlan.Parameters.defaults(),
                org.samlier.core.plan.TestPlan.Interaction.defaults(),
                org.samlier.core.run.Reachability.CONFIRMED,
                new TranscriptRecorder() {
                    @Override public TranscriptEntry record(TranscriptInput input) {
                        throw new UnsupportedOperationException();
                    }
                    @Override public TranscriptEntry updateSamlAnalysis(
                            String entryId, String correlationId, Map<String, Object> samlSummary) {
                        throw new UnsupportedOperationException();
                    }
                    @Override public List<TranscriptEntry> list(String runId) { return entries; }
                }, true);
    }

    private TranscriptEntry fetch(String variant, int sequence) {
        return entry(sequence, "/metadata/live", 0,
                Map.of("type", "MetadataFetch", "variant", variant, "feed", "live"));
    }

    private TranscriptEntry use(String variant, int sequence) {
        return entry(sequence, "https://suite.example/p/plan/sp/acs/0?mdv=" + variant + "&run=" + RUN,
                10, Map.of("type", "SAMLResponse"));
    }

    private TranscriptEntry entry(
            int sequence, String url, int decodedBytes, Map<String, Object> summary) {
        return new TranscriptEntry(
                "tr_" + sequence, RUN, Direction.INBOUND, NOW.plusSeconds(sequence), "corr", "GET", url,
                200, Map.of(), null, 0, decodedBytes > 0 ? "decoded" : null, decodedBytes,
                null, null, summary);
    }
}
