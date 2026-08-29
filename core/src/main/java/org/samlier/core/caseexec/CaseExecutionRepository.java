package org.samlier.core.caseexec;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CaseExecutionRepository {
    Optional<CaseExecution> find(String runId, String caseId);

    /** Returns every persisted case execution for a Run in stable case-ID order. */
    List<CaseExecution> list(String runId);

    /**
     * Persists the next case state and all send intents in one transaction.
     * expectedRevision is -1 for creation. A false result means another worker won the transition.
     */
    boolean apply(long expectedRevision, CaseExecution execution, List<OutboundAction> actions);

    List<OutboxEntry> listOutbox(String runId);

    Optional<OutboxEntry> findOutbox(String actionId);

    boolean transitionOutbox(
            String actionId,
            OutboxStatus expected,
            OutboxStatus next,
            Map<String, Object> sendResult,
            String transcriptEntryId,
            Instant updatedAt);

    /** A process restart makes every in-flight send uncertain. */
    int recoverSendingAsUnknownDelivery(Instant updatedAt);
}
