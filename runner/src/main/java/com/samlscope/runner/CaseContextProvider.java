package com.samlscope.runner;

import com.samlscope.core.caseexec.CaseContext;

@FunctionalInterface
public interface CaseContextProvider {
    CaseContext contextFor(String runId);
}
