package org.samlier.runner;

import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.evaluation.CaseOutcome;

@FunctionalInterface
public interface BrowserCompletionExecutor {
    Result complete(String runId, String caseId);

    record Result(String runId, String caseId, CaseExecutionStatus status, CaseOutcome outcome) {}
}
