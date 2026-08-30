package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboxEntry;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.cases.BrowserPrompt;
import org.samlier.runner.cases.ProtocolEvidenceCase;

class TranscriptAutomationRecorderTest {
    private static final String RUN = "run";
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void notifiesAfterDurableRecordAndDoesNotBreakProtocolTrafficWhenReconciliationFails() {
        var delegate = new MemoryRecorder();
        var recorder = new TranscriptAutomationRecorder(delegate, delegate);
        var calls = new AtomicInteger();
        recorder.onRecorded(runId -> {
            calls.incrementAndGet();
            throw new IllegalStateException("retry on the next observation");
        });

        var recorded = recorder.record(input());
        recorder.updateSamlAnalysis(recorded.id(), "request", Map.of("type", "Response"));

        assertEquals(2, calls.get());
        assertEquals(1, recorder.list("run").size());
        assertThrows(IllegalStateException.class, () -> recorder.onRecorded(ignored -> { }));
    }

    @Test
    void completedSamlAnalysisFinishesAWaitingCaseWithoutAnOperatorCompletionEvent() {
        var delegate = new MemoryRecorder();
        var recorder = new TranscriptAutomationRecorder(delegate, delegate);
        var repository = new MemoryExecutions();
        var transitions = new CaseExecutionService(repository);
        var testCase = new ResponseTranscriptCase();
        var context = new DefaultCaseContext(
                RUN, TargetRole.IDP, Clock.fixed(NOW, ZoneOffset.UTC),
                TestPlan.Parameters.defaults(), TestPlan.Interaction.defaults(),
                Reachability.CONFIRMED, recorder, true);
        transitions.start(RUN, testCase, context);
        var automation = new ProtocolEvidenceAutomationService(
                repository, new TestCaseRegistry(List.of(testCase)), transitions, ignored -> context);
        recorder.onRecorded(automation::evaluateReady);

        var entry = recorder.record(input());
        assertEquals(CaseExecutionStatus.WAITING_BROWSER,
                repository.find(RUN, testCase.id()).orElseThrow().status());

        recorder.updateSamlAnalysis(entry.id(), "request", Map.of("type", "Response"));

        var finished = repository.find(RUN, testCase.id()).orElseThrow();
        assertEquals(CaseExecutionStatus.FINISHED, finished.status());
        assertEquals(Outcome.SATISFIED, finished.outcome().outcome());
    }

    private TranscriptInput input() {
        return new TranscriptInput(
                RUN, Direction.INBOUND, NOW, "corr", "POST",
                "https://suite.example/acs", 200, Map.of(), new byte[0],
                "application/x-www-form-urlencoded", null, "<Response/>".getBytes(), Map.of());
    }

    private static final class MemoryRecorder implements TranscriptRecorder, TranscriptContentReader {
        private TranscriptEntry entry;
        @Override public TranscriptEntry record(TranscriptInput input) {
            entry = new TranscriptEntry(
                    "tx", input.runId(), input.direction(), input.timestamp(), input.correlationId(),
                    input.method(), input.url(), input.status(), input.headers(), null, 0,
                    "decoded", input.decodedSaml().length, input.contentType(), input.rawQuery(),
                    input.samlSummary());
            return entry;
        }
        @Override public TranscriptEntry updateSamlAnalysis(
                String entryId, String correlationId, Map<String, Object> summary) {
            entry = new TranscriptEntry(
                    entry.id(), entry.runId(), entry.direction(), entry.timestamp(), correlationId,
                    entry.method(), entry.url(), entry.status(), entry.headers(), entry.bodyRef(),
                    entry.bodyBytes(), entry.decodedSamlRef(), entry.decodedSamlBytes(),
                    entry.contentType(), entry.rawQuery(), Map.copyOf(summary));
            return entry;
        }
        @Override public List<TranscriptEntry> list(String runId) { return List.of(entry); }
        @Override public byte[] readDecodedSaml(TranscriptEntry value) { return "<Response/>".getBytes(); }
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
                String actionId, OutboxStatus expected, OutboxStatus next,
                Map<String, Object> sendResult, String transcriptEntryId, Instant updatedAt) {
            return false;
        }
        @Override public int recoverSendingAsUnknownDelivery(Instant updatedAt) { return 0; }
    }

    private static final class ResponseTranscriptCase
            implements TestCase, BrowserPrompt, ProtocolEvidenceCase {
        @Override public String id() { return "IIP-SSO03-a-idp-01"; }
        @Override public TargetRole role() { return TargetRole.IDP; }
        @Override public String browserInstructionsEn() { return "Perform the ordinary SSO flow."; }
        @Override public CaseStep start(CaseContext context) {
            return new CaseStep.AwaitBrowser(
                    new CaseState("await-response", Map.of()), List.of(),
                    java.net.URI.create("https://suite.example/start"), java.time.Duration.ofMinutes(5));
        }
        @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
            if (!(event instanceof CaseEvent.TranscriptReady)) {
                throw new IllegalArgumentException("Transcript evidence is required");
            }
            return new CaseStep.Finish(CaseOutcome.of(
                    Outcome.SATISFIED, "transcript-ready", List.of()));
        }
        @Override public EvidenceStatus evidenceStatus(CaseContext context) {
            var ready = context.transcript().list(context.runId()).stream()
                    .anyMatch(entry -> "Response".equals(entry.samlSummary().get("type")));
            return new EvidenceStatus(
                    ready, List.of("response"), ready ? List.of("response") : List.of(), Map.of());
        }
    }
}
