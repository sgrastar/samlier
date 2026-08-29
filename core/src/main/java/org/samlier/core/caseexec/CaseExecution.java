package org.samlier.core.caseexec;

import java.time.Instant;
import java.util.Objects;
import org.samlier.core.evaluation.CaseOutcome;

public record CaseExecution(
        String runId,
        String caseId,
        long revision,
        CaseExecutionStatus status,
        CaseState state,
        WaitCondition waitCondition,
        CaseOutcome outcome,
        Instant updatedAt) {

    public CaseExecution {
        text(runId, "runId");
        text(caseId, "caseId");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(updatedAt, "updatedAt");
        var waiting = switch (status) {
            case WAITING_BROWSER, WAITING_CONFIG, WAITING_ATTESTATION, WAITING_INBOUND -> true;
            default -> false;
        };
        if (waiting != (waitCondition != null)) {
            throw new IllegalArgumentException("Waiting executions must carry exactly one wait condition");
        }
        if ((status == CaseExecutionStatus.FINISHED) != (outcome != null)) {
            throw new IllegalArgumentException("Only FINISHED executions carry an outcome");
        }
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
