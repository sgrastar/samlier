package org.samlier.runner;

import java.util.List;
import java.util.Objects;
import org.samlier.core.evaluation.ApplicabilityInput;
import org.samlier.core.evaluation.ApplicabilityInputRepository;
import org.samlier.core.evaluation.PredicateCatalog.Definition;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.TestRun;

/** Reads explicitly combined applicability facts; absence remains UNKNOWN. */
public final class PersistedApplicabilityInputProvider implements ApplicabilityInputProvider {
    private final ApplicabilityInputRepository repository;

    public PersistedApplicabilityInputProvider(ApplicabilityInputRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public ApplicabilityInput input(Definition predicate, TestRun run, TestPlan plan) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(plan, "plan");
        if (!run.planId().equals(plan.id())) {
            throw new IllegalArgumentException("Run does not belong to the supplied plan");
        }
        return repository.find(run.id(), predicate.key())
                .orElseGet(() -> new ApplicabilityInput(null, null, List.of(), null));
    }
}
