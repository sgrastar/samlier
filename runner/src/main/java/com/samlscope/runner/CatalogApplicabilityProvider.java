package com.samlscope.runner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.samlscope.core.evaluation.ApplicabilityEngine;
import com.samlscope.core.evaluation.ApplicabilityEvaluation;
import com.samlscope.core.evaluation.ApplicabilityInput;
import com.samlscope.core.evaluation.CoverageCatalog;
import com.samlscope.core.evaluation.PredicateCatalog;
import com.samlscope.core.plan.TestPlan;
import com.samlscope.core.run.TestRun;

/** Evaluates every selected conditional obligation using its approved predicate definition. */
public final class CatalogApplicabilityProvider implements ApplicabilityProvider {
    private final CoverageCatalog coverage;
    private final Map<String, PredicateCatalog.Definition> predicates;
    private final ApplicabilityInputProvider inputs;

    public CatalogApplicabilityProvider(
            CoverageCatalog coverage,
            PredicateCatalog predicates,
            ApplicabilityInputProvider inputs) {
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.predicates = Objects.requireNonNull(predicates, "predicates").byKey();
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        for (var obligation : coverage.obligations()) {
            if (obligation.condition() != null && !this.predicates.containsKey(obligation.condition())) {
                throw new IllegalArgumentException(
                        "Obligation references an unknown predicate: " + obligation.key());
            }
        }
    }

    @Override
    public java.util.List<ApplicabilityEvaluation> evaluations(TestRun run, TestPlan plan) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(plan, "plan");
        var predicateInputs = new LinkedHashMap<String, ApplicabilityInput>();
        var result = new ArrayList<ApplicabilityEvaluation>();
        for (var obligation : coverage.obligations()) {
            if (!obligation.includedIn(plan.profile()) || obligation.condition() == null) continue;
            var definition = predicates.get(obligation.condition());
            var input = predicateInputs.computeIfAbsent(definition.key(), ignored ->
                    Objects.requireNonNull(inputs.input(definition, run, plan),
                            "Applicability input provider returned null for " + definition.key()));
            result.add(ApplicabilityEngine.evaluate(
                    obligation.key(), definition.key(), definition.kind(), input));
        }
        return java.util.List.copyOf(result);
    }
}
