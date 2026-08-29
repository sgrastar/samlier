package org.samlier.runner;

import java.util.Objects;
import org.samlier.core.caseexec.CaseEvent;

/** Resumes a persisted attestation wait without accepting a client-supplied Outcome or Verdict. */
public final class AttestationService implements AttestationExecutor {
    private final TestCaseRegistry registry;
    private final CaseExecutionService executions;
    private final CaseContextProvider contexts;

    public AttestationService(
            TestCaseRegistry registry,
            CaseExecutionService executions,
            CaseContextProvider contexts) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.executions = Objects.requireNonNull(executions, "executions");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @Override
    public Result attest(String runId, String caseId, String value, String note) {
        text(runId, "runId");
        text(caseId, "caseId");
        text(value, "value");
        var testCase = registry.require(caseId);
        var context = contexts.contextFor(runId);
        var execution = executions.resume(
                runId, testCase, context, new CaseEvent.Attested(value, note));
        return new Result(runId, caseId, execution.status(), execution.outcome());
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
