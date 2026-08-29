package org.samlier.core.caseexec;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record OutboxEntry(
        String runId,
        String caseId,
        OutboundAction action,
        OutboxStatus status,
        Map<String, Object> sendResult,
        String transcriptEntryId,
        Instant createdAt,
        Instant updatedAt) {

    public OutboxEntry {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId must not be blank");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(status, "status");
        sendResult = new CaseState("send-result", sendResult == null ? Map.of() : sendResult).data();
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
