package com.samlscope.runner;

import java.util.List;
import com.samlscope.core.evaluation.ApplicabilityEvaluation;
import com.samlscope.core.plan.TestPlan;
import com.samlscope.core.run.TestRun;

@FunctionalInterface
public interface ApplicabilityProvider {
    List<ApplicabilityEvaluation> evaluations(TestRun run, TestPlan plan);
}
