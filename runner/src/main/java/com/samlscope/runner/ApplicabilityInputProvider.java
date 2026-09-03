package com.samlscope.runner;

import com.samlscope.core.evaluation.ApplicabilityInput;
import com.samlscope.core.evaluation.PredicateCatalog.Definition;
import com.samlscope.core.plan.TestPlan;
import com.samlscope.core.run.TestRun;

/** Supplies one already-combined declaration/observation input for an approved predicate. */
@FunctionalInterface
public interface ApplicabilityInputProvider {
    ApplicabilityInput input(Definition predicate, TestRun run, TestPlan plan);
}
