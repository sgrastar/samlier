package com.samlscope.runner;

import com.samlscope.core.caseexec.CaseExecutionStatus;
import com.samlscope.core.evaluation.CaseOutcome;

@FunctionalInterface
public interface AttestationExecutor {
    Result attest(String runId, String caseId, String value, String note);

    record Result(String runId, String caseId, CaseExecutionStatus status, CaseOutcome outcome) {}
}
