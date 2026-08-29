package org.samlier.runner;

import org.samlier.core.caseexec.CaseContext;

@FunctionalInterface
public interface CaseContextProvider {
    CaseContext contextFor(String runId);
}
