package com.samlscope.runner;

import com.samlscope.core.caseexec.CaseExecutionStatus;
import com.samlscope.core.evaluation.CaseOutcome;

@FunctionalInterface
public interface BrowserCompletionExecutor {
    Result complete(String runId, String caseId);

    record Result(String runId, String caseId, CaseExecutionStatus status, CaseOutcome outcome) {}
}
