package org.samlier.runner;

import org.samlier.core.evaluation.ApplicabilityInput;
import org.samlier.core.evaluation.PredicateCatalog.Definition;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.TestRun;

/** Supplies one already-combined declaration/observation input for an approved predicate. */
@FunctionalInterface
public interface ApplicabilityInputProvider {
    ApplicabilityInput input(Definition predicate, TestRun run, TestPlan plan);
}
