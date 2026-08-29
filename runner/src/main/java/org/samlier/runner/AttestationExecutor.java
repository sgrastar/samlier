package org.samlier.runner;

import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.evaluation.CaseOutcome;

@FunctionalInterface
public interface AttestationExecutor {
    Result attest(String runId, String caseId, String value, String note);

    record Result(String runId, String caseId, CaseExecutionStatus status, CaseOutcome outcome) {}
}
