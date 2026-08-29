package org.samlier.runner;

import java.util.List;
import org.samlier.core.evaluation.CaseRun;

@FunctionalInterface
public interface CaseRunProvider {
    List<CaseRun> completed(String runId);
}
