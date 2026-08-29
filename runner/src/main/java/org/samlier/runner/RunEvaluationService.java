package org.samlier.runner;

import java.util.Objects;
import org.samlier.core.evaluation.CoverageCatalog;
import org.samlier.core.evaluation.Evaluator;
import org.samlier.core.evaluation.RunResult;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.run.RunRepository;

/** The runtime's single entry point into the canonical Evaluator. */
public final class RunEvaluationService {
    private final CoverageCatalog catalog;
    private final PlanRepository plans;
    private final RunRepository runs;
    private final CaseRunProvider caseRuns;
    private final ApplicabilityProvider applicability;
    private final SuiteIncidentProvider incidents;

    public RunEvaluationService(
            CoverageCatalog catalog,
            PlanRepository plans,
            RunRepository runs,
            CaseRunProvider caseRuns,
            ApplicabilityProvider applicability,
            SuiteIncidentProvider incidents) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.caseRuns = Objects.requireNonNull(caseRuns, "caseRuns");
        this.applicability = Objects.requireNonNull(applicability, "applicability");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
    }

    public RunResult evaluate(String runId) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
        var plan = plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
        return Evaluator.evaluate(
                catalog,
                plan,
                applicability.evaluations(run, plan),
                caseRuns.completed(runId),
                incidents.incidents(runId));
    }
}
