package org.samlier.runner;

import java.util.List;
import org.samlier.core.evaluation.SuiteIncident;

@FunctionalInterface
public interface SuiteIncidentProvider {
    List<SuiteIncident> incidents(String runId);
}
