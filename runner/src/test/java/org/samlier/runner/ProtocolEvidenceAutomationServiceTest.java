package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboxEntry;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.cases.MetadataConsumerObservationTestCase;

class ProtocolEvidenceAutomationServiceTest {
    private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void advancesOnlyAnEvidenceReadyCaseAndLetsTheCaseDeriveItsOutcome() {
        var repository = new MemoryExecutions();
        var transitions = new CaseExecutionService(repository);
        var testCase = new MetadataConsumerObservationTestCase(
                "IIP-MD05-ao-sp-01", TargetRole.SP,
                MetadataConsumerObservationTestCase.Rule.OMITTED_KEY_INFO);
        var entries = List.of(fetch("control", 1), use("control", 2), fetch("no-key-info", 3));
        var context = context(entries);
        transitions.start(RUN, testCase, context);
        var service = new ProtocolEvidenceAutomationService(
                repository, new TestCaseRegistry(List.of(testCase)), transitions, ignored -> context);

        var before = service.status(RUN);
        assertEquals(1, before.eligibleCases());
        assertEquals(1, before.readyCases());

        var evaluation = service.evaluateReady(RUN);
        assertEquals(List.of(new ProtocolEvidenceAutomationService.CompletedCase(
                testCase.id(), Outcome.VIOLATED)), evaluation.completed());
        assertEquals(0, evaluation.remaining().eligibleCases());
        assertEquals(CaseExecutionStatus.FINISHED,
                repository.find(RUN, testCase.id()).orElseThrow().status());
    }

    @Test
    void doesNotConvertAnIncompleteProbeIntoNotVerified() {
        var repository = new MemoryExecutions();
        var transitions = new CaseExecutionService(repository);
        var testCase = new MetadataConsumerObservationTestCase(
                "IIP-MD05-an-sp-01", TargetRole.SP,
                MetadataConsumerObservationTestCase.Rule.EXCLUDED_CONTENT);
        var context = context(List.of(fetch("control", 1), use("control", 2)));
        transitions.start(RUN, testCase, context);
        var service = new ProtocolEvidenceAutomationService(
                repository, new TestCaseRegistry(List.of(testCase)), transitions, ignored -> context);

        assertEquals(0, service.status(RUN).readyCases());
        assertEquals(List.of(), service.evaluateReady(RUN).completed());
        assertEquals(CaseExecutionStatus.WAITING_CONFIG,
                repository.find(RUN, testCase.id()).orElseThrow().status());
    }

    private static CaseContext context(List<TranscriptEntry> entries) {
        return new DefaultCaseContext(
                RUN, TargetRole.SP, Clock.fixed(NOW, ZoneOffset.UTC), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), Reachability.CONFIRMED, new MemoryTranscript(entries), true);
    }

    private static TranscriptEntry fetch(String variant, int sequence) {
        return entry(sequence, "/metadata/live", 0,
                Map.of("type", "MetadataFetch", "variant", variant, "feed", "live"));
    }

    private static TranscriptEntry use(String variant, int sequence) {
        return entry(sequence, "https://suite.example/p/plan/sp/acs/0?mdv=" + variant + "&run=" + RUN,
                10, Map.of("type", "SAMLResponse"));
    }

    private static TranscriptEntry entry(
            int sequence, String url, int decodedBytes, Map<String, Object> summary) {
        return new TranscriptEntry(
                "tr_" + sequence, RUN, Direction.INBOUND, NOW.plusSeconds(sequence), "corr", "GET", url,
                200, Map.of(), null, 0, decodedBytes > 0 ? "decoded" : null, decodedBytes,
                null, null, summary);
    }

    private record MemoryTranscript(List<TranscriptEntry> entries) implements TranscriptRecorder {
        @Override public TranscriptEntry record(TranscriptInput input) { throw new UnsupportedOperationException(); }
        @Override public TranscriptEntry updateSamlAnalysis(
                String entryId, String correlationId, Map<String, Object> samlSummary) {
            throw new UnsupportedOperationException();
        }
        @Override public List<TranscriptEntry> list(String runId) { return entries; }
    }

    private static final class MemoryExecutions implements CaseExecutionRepository {
        private final Map<String, CaseExecution> values = new LinkedHashMap<>();

        @Override public Optional<CaseExecution> find(String runId, String caseId) {
            return Optional.ofNullable(values.get(runId + "|" + caseId));
        }
        @Override public List<CaseExecution> list(String runId) {
            return values.values().stream().filter(value -> value.runId().equals(runId)).toList();
        }
        @Override public boolean apply(
                long expectedRevision, CaseExecution execution, List<OutboundAction> actions) {
            var key = execution.runId() + "|" + execution.caseId();
            var current = values.get(key);
            if ((current == null ? -1 : current.revision()) != expectedRevision) return false;
            values.put(key, execution);
            return true;
        }
        @Override public List<OutboxEntry> listOutbox(String runId) { return List.of(); }
        @Override public Optional<OutboxEntry> findOutbox(String actionId) { return Optional.empty(); }
        @Override public boolean transitionOutbox(
                String actionId, OutboxStatus expected, OutboxStatus next, Map<String, Object> sendResult,
                String transcriptEntryId, Instant updatedAt) { return false; }
        @Override public int recoverSendingAsUnknownDelivery(Instant updatedAt) { return 0; }
    }
}
