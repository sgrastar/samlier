package com.samlscope.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseExecution;
import com.samlscope.core.plan.PlanProfile;
import com.samlscope.runner.cases.AutomatedCaseRegistry;

/** Starts approved automated cases at the lifecycle point where their evidence is available. */
public final class AutomatedCaseRunner {
    private final TestCaseRegistry cases;
    private final CaseExecutionService executions;

    public AutomatedCaseRunner(TestCaseRegistry cases, CaseExecutionService executions) {
        this.cases = Objects.requireNonNull(cases, "cases");
        this.executions = Objects.requireNonNull(executions, "executions");
    }

    public List<CaseExecution> startReady(String runId, PlanProfile profile, CaseContext context) {
        if (profile == null || profile.role() != context.targetRole()) {
            throw new IllegalArgumentException("Plan profile belongs to another target role");
        }
        var started = new ArrayList<CaseExecution>();
        for (var testCase : cases.forRole(context.targetRole())) {
            if (!AutomatedCaseRegistry.includedIn(testCase.id(), profile)) continue;
            var duringRun = AutomatedCaseRegistry.runsDuringRun(testCase.id());
            if (!duringRun && !context.transcriptComplete()) continue;
            started.add(executions.start(runId, testCase, context));
        }
        return List.copyOf(started);
    }
}
