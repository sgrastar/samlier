package com.samlscope.runner;

import java.util.List;
import com.samlscope.core.evaluation.CaseRun;

@FunctionalInterface
public interface CaseRunProvider {
    List<CaseRun> completed(String runId);
}
