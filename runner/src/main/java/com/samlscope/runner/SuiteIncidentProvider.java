package com.samlscope.runner;

import java.util.List;
import com.samlscope.core.evaluation.SuiteIncident;

@FunctionalInterface
public interface SuiteIncidentProvider {
    List<SuiteIncident> incidents(String runId);
}
