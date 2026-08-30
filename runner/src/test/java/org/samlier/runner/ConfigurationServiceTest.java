package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboxEntry;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.runner.cases.ConfigurationPrompt;
import org.samlier.runner.cases.ProtocolEvidenceCase;

class ConfigurationServiceTest {
    @Test
    void operatorCannotConfirmATranscriptDrivenConfigurationCase() {
        var testCase = new TranscriptConfigurationCase();
        var service = new ConfigurationService(
                new TestCaseRegistry(List.of(testCase)), nullExecutions(),
                ignored -> { throw new AssertionError("context must not be requested"); });

        assertThrows(IllegalStateException.class,
                () -> service.answer("run", testCase.id(), "confirmed", ""));
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

    private static final class TranscriptConfigurationCase
            implements TestCase, ConfigurationPrompt, ProtocolEvidenceCase {
        @Override public String id() { return "IIP-MD05-an-sp-01"; }
        @Override public TargetRole role() { return TargetRole.SP; }
        @Override public String instructionEn() { return "Use the stable Suite metadata URL."; }
        @Override public CaseStep start(CaseContext context) { throw new UnsupportedOperationException(); }
        @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
            throw new AssertionError("operator confirmation must not reach the case");
        }
        @Override public EvidenceStatus evidenceStatus(CaseContext context) {
            return new EvidenceStatus(false, List.of("metadata-fetch"), List.of(), Map.of());
        }
    }
}
