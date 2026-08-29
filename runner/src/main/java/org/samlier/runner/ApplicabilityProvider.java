package org.samlier.runner;

import java.util.List;
import org.samlier.core.evaluation.ApplicabilityEvaluation;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.TestRun;

@FunctionalInterface
public interface ApplicabilityProvider {
    List<ApplicabilityEvaluation> evaluations(TestRun run, TestPlan plan);
}
