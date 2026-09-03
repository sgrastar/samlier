package com.samlscope.core.run;

public enum RunStatus {
    CREATED,
    PREFLIGHT,
    RUNNING,
    WAITING_BROWSER,
    WAITING_CONFIG,
    WAITING_ATTEST,
    WAITING_CREDENTIAL,
    COMPLETED,
    ABORTED
}
