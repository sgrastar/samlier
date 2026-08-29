package org.samlier.runner;

import java.util.List;
import java.util.Objects;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.evaluation.SuiteIncident;

/** Reconstructs unresolved delivery incidents from the durable outbox state. */
public final class OutboxIncidentProjection implements SuiteIncidentProvider {
    private final CaseExecutionRepository repository;

    public OutboxIncidentProjection(CaseExecutionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public List<SuiteIncident> incidents(String runId) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        return repository.listOutbox(runId).stream()
                .filter(entry -> entry.status() == OutboxStatus.UNKNOWN_DELIVERY)
                .map(entry -> new SuiteIncident(
                        "UNKNOWN_DELIVERY", entry.caseId(), entry.action().actionId(),
                        "Delivery remains unknown; this is Suite uncertainty, not target nonconformance"))
                .toList();
    }
}
