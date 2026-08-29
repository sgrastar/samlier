package org.samlier.runner;

import java.util.Objects;
import org.samlier.core.caseexec.CaseEvent;

/** Advances a browser case without accepting any client-supplied outcome. */
public final class BrowserCompletionService implements BrowserCompletionExecutor {
    private final TestCaseRegistry registry;
    private final CaseExecutionService executions;
    private final CaseContextProvider contexts;

    public BrowserCompletionService(
            TestCaseRegistry registry, CaseExecutionService executions, CaseContextProvider contexts) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.executions = Objects.requireNonNull(executions, "executions");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @Override
    public Result complete(String runId, String caseId) {
        if (runId == null || runId.isBlank() || caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("Run and case IDs must not be blank");
        }
        var execution = executions.resume(
                runId, registry.require(caseId), contexts.contextFor(runId),
                new CaseEvent.BrowserReturned("operator-completed-approved-steps"));
        return new Result(runId, caseId, execution.status(), execution.outcome());
    }
}
