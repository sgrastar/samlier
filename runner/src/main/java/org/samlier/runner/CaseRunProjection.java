package org.samlier.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.CaseIds;
import org.samlier.core.evaluation.CaseRun;

/** Projects persisted finished executions into the Evaluator's case-side input. */
public final class CaseRunProjection {
    private final CaseExecutionRepository repository;
    private final TestCaseRegistry registry;

    public CaseRunProjection(CaseExecutionRepository repository, TestCaseRegistry registry) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public List<CaseRun> completed(String runId) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        var projected = new ArrayList<CaseRun>();
        for (var execution : repository.list(runId)) {
            registry.require(execution.caseId());
            if (execution.status() != CaseExecutionStatus.FINISHED) continue;
            if (execution.outcome() == null) {
                throw new IllegalStateException("Finished case has no outcome: " + execution.caseId());
            }
            projected.add(CaseRun.completed(
                    execution.caseId(), CaseIds.obligationKey(execution.caseId()), execution.outcome()));
        }
        return List.copyOf(projected);
    }
}
