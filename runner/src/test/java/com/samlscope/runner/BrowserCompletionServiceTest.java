package com.samlscope.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseExecution;
import com.samlscope.core.caseexec.CaseExecutionRepository;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.OutboundAction;
import com.samlscope.core.caseexec.OutboxEntry;
import com.samlscope.core.caseexec.OutboxStatus;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.runner.cases.BrowserPrompt;
import com.samlscope.runner.cases.ProtocolEvidenceCase;

class BrowserCompletionServiceTest {
    @Test
    void operatorCannotCompleteATranscriptDrivenBrowserCase() {
        var testCase = new TranscriptBrowserCase();
        var service = new BrowserCompletionService(
                new TestCaseRegistry(List.of(testCase)), nullExecutions(),
                ignored -> { throw new AssertionError("context must not be requested"); });

        assertThrows(IllegalStateException.class,
                () -> service.complete("run", testCase.id()));
    }

    private CaseExecutionService nullExecutions() {
        return new CaseExecutionService(new CaseExecutionRepository() {
            @Override public Optional<CaseExecution> find(String runId, String caseId) {
                return Optional.empty();
            }
            @Override public List<CaseExecution> list(String runId) { return List.of(); }
            @Override public boolean apply(
                    long expectedRevision, CaseExecution execution, List<OutboundAction> actions) {
                throw new AssertionError("execution repository must not be used");
            }
            @Override public List<OutboxEntry> listOutbox(String runId) { return List.of(); }
            @Override public Optional<OutboxEntry> findOutbox(String actionId) { return Optional.empty(); }
            @Override public boolean transitionOutbox(
                    String actionId, OutboxStatus expected, OutboxStatus next,
                    Map<String, Object> sendResult, String transcriptEntryId, Instant updatedAt) {
                throw new AssertionError("outbox must not be used");
            }
            @Override public int recoverSendingAsUnknownDelivery(Instant updatedAt) { return 0; }
        });
    }

    private static final class TranscriptBrowserCase
            implements TestCase, BrowserPrompt, ProtocolEvidenceCase {
        @Override public String id() { return "IIP-SSO03-a-idp-01"; }
        @Override public TargetRole role() { return TargetRole.IDP; }
        @Override public String browserInstructionsEn() { return "Run the correlated flow."; }
        @Override public CaseStep start(CaseContext context) { throw new UnsupportedOperationException(); }
        @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
            throw new AssertionError("operator completion must not reach the case");
        }
        @Override public EvidenceStatus evidenceStatus(CaseContext context) {
            return new EvidenceStatus(false, List.of("response"), List.of(), Map.of());
        }
    }
}
