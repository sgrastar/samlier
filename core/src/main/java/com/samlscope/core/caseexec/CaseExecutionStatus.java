package com.samlscope.core.caseexec;

public enum CaseExecutionStatus {
    RUNNING,
    WAITING_BROWSER,
    WAITING_CONFIG,
    WAITING_ATTESTATION,
    WAITING_INBOUND,
    FINISHED
}
