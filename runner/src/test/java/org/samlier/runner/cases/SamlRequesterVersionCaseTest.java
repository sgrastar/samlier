package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboxEntry;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;

class SamlRequesterVersionCaseTest {
    @Test
    void acceptsVersionTwoOnlyWhenNormalVersionTwoSsoWasProven() {
        var rule = new SamlRequesterVersionCase(repository(CaseOutcome.of(
                Outcome.SATISFIED, "normal-sso.satisfied", List.of(new EvidenceRef("transcript", "normal-sso")))));

        var outcome = rule.evaluate("run", List.of(request("2.0")));

        assertEquals(Outcome.SATISFIED, outcome.outcome());
        assertEquals(2, outcome.evidence().size());
    }

    @Test
    void anEmittedNonTwoRequestIsAProtocolViolationRegardlessOfNormalFlowState() {
        var rule = new SamlRequesterVersionCase(repository(null));

        assertEquals(Outcome.VIOLATED, rule.evaluate("run", List.of(request("1.1"))).outcome());
    }

    @Test
    void inabilityToProcessTheMatchingResponseVersionIsAViolation() {
        var rule = new SamlRequesterVersionCase(repository(CaseOutcome.of(
                Outcome.VIOLATED, "normal-sso.violated", List.of())));

        assertEquals(Outcome.VIOLATED, rule.evaluate("run", List.of(request("2.0"))).outcome());
    }

    @Test
    void absentOrInconclusiveNormalFlowEvidenceRemainsNotVerified() {
        assertEquals(Outcome.NOT_VERIFIED,
                new SamlRequesterVersionCase(repository(null)).evaluate("run", List.of(request("2.0"))).outcome());
        assertEquals(Outcome.NOT_VERIFIED,
                new SamlRequesterVersionCase(repository(CaseOutcome.notVerified("environment", "normal-sso.unverified")))
                        .evaluate("run", List.of(request("2.0"))).outcome());
    }

    private TargetTranscriptMessages.Message request(String version) {
        var xml = "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" Version=\""
                + version + "\"/>";
        return new TargetTranscriptMessages.Message("request", xml.getBytes(StandardCharsets.UTF_8));
    }

    private CaseExecutionRepository repository(CaseOutcome outcome) {
        return new CaseExecutionRepository() {
            @Override public Optional<CaseExecution> find(String runId, String caseId) {
                if (outcome == null) return Optional.empty();
                return Optional.of(new CaseExecution(
                        runId, caseId, 0, CaseExecutionStatus.FINISHED, CaseState.initial(), null,
                        outcome, Instant.parse("2026-08-29T00:00:00Z")));
            }
            @Override public boolean apply(long expectedRevision, CaseExecution execution, List<OutboundAction> actions) {
                throw new UnsupportedOperationException();
            }
            @Override public List<OutboxEntry> listOutbox(String runId) { throw new UnsupportedOperationException(); }
            @Override public Optional<OutboxEntry> findOutbox(String actionId) { throw new UnsupportedOperationException(); }
            @Override public boolean transitionOutbox(String actionId, OutboxStatus expected, OutboxStatus next,
                    Map<String, Object> sendResult, String transcriptEntryId, Instant updatedAt) {
                throw new UnsupportedOperationException();
            }
            @Override public int recoverSendingAsUnknownDelivery(Instant updatedAt) { throw new UnsupportedOperationException(); }
        };
    }
}
