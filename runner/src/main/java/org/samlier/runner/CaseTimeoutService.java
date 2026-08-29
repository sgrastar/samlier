package org.samlier.runner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;

/** Converts expired waits into explicit case events instead of leaving Runs stuck indefinitely. */
public final class CaseTimeoutService {
    private final CaseExecutionRepository repository;
    private final TestCaseRegistry registry;
    private final CaseExecutionService executions;

    public CaseTimeoutService(
            CaseExecutionRepository repository,
            TestCaseRegistry registry,
            CaseExecutionService executions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.executions = Objects.requireNonNull(executions, "executions");
    }

    public List<CaseExecution> expireReady(String runId, CaseContext context) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        if (!runId.equals(context.runId())) throw new IllegalArgumentException("CaseContext belongs to another Run");
        var now = context.clock().instant();
        var expired = new ArrayList<CaseExecution>();
        for (var execution : repository.list(runId)) {
            var wait = execution.waitCondition();
            if (wait == null || now.isBefore(wait.expiresAt())) continue;
            var testCase = registry.require(execution.caseId());
            var waited = Duration.between(execution.updatedAt(), now);
            if (waited.isNegative()) waited = Duration.ZERO;
            expired.add(executions.resume(
                    runId, testCase, context, new CaseEvent.TimedOut(waited)));
        }
        return List.copyOf(expired);
    }
}
