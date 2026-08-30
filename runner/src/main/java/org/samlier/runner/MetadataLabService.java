package org.samlier.runner;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.run.RunRepository;
import org.samlier.saml.metadata.MetadataService;

/**
 * Selects a controlled metadata fixture behind one stable, Run-scoped URL.
 *
 * <p>The target is configured through standard SAML metadata only. No product administration API
 * is involved. Selection is persisted in the Run context so a process restart cannot silently
 * change the fixture being served.</p>
 */
public final class MetadataLabService {
    private static final String CONTEXT_KEY = "metadata_lab";
    private static final String SELECTED_VARIANT = "selected_variant";

    private final URI peerBase;
    private final PlanRepository plans;
    private final RunRepository runs;
    private final RunService runService;
    private final Clock clock;

    public MetadataLabService(
            URI peerBase,
            PlanRepository plans,
            RunRepository runs,
            RunService runService,
            Clock clock) {
        this.peerBase = Objects.requireNonNull(peerBase, "peerBase");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.runService = Objects.requireNonNull(runService, "runService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public State state(String runId) {
        var run = requireRun(runId);
        plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
        var selected = selected(run.context());
        return new State(
                run.id(), run.planId(), selected.id(), liveUrl(run.planId(), run.id()),
                java.util.Arrays.stream(MetadataService.Variant.values())
                        .filter(value -> value != MetadataService.Variant.BASELINE)
                        .map(MetadataService.Variant::id).toList());
    }

    public State select(String runId, String variantId) {
        var run = requireRun(runId);
        var variant = MetadataService.Variant.parse(required(variantId, "variant"));
        if (variant == MetadataService.Variant.BASELINE) {
            throw new IllegalArgumentException(
                    "The live metadata lab requires a Run-correlated fixture; use control instead of baseline");
        }
        var context = new LinkedHashMap<String, Object>(run.context());
        context.put(CONTEXT_KEY, Map.of(
                SELECTED_VARIANT, variant.id(),
                "selected_at", clock.instant().toString()));
        runService.update(run, run.status(), run.targetToSuiteReachability(), context);
        return state(runId);
    }

    public MetadataService.Variant selected(String runId, String planId) {
        var run = requireRun(runId);
        if (!run.planId().equals(required(planId, "planId"))) {
            throw new IllegalArgumentException("Metadata lab Run belongs to another Test Plan");
        }
        return selected(run.context());
    }

    private MetadataService.Variant selected(Map<String, Object> context) {
        var lab = context.get(CONTEXT_KEY);
        if (!(lab instanceof Map<?, ?> values)) return MetadataService.Variant.CONTROL;
        var selected = values.get(SELECTED_VARIANT);
        return selected instanceof String value
                ? MetadataService.Variant.parse(value)
                : MetadataService.Variant.CONTROL;
    }

    private URI liveUrl(String planId, String runId) {
        return peerBase.resolve("/p/" + planId + "/metadata/live?run=" + runId);
    }

    private org.samlier.core.run.TestRun requireRun(String runId) {
        return runs.find(required(runId, "runId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public record State(
            String runId,
            String planId,
            String selectedVariant,
            URI metadataUrl,
            java.util.List<String> availableVariants) {
        public State {
            availableVariants = java.util.List.copyOf(availableVariants);
        }
    }
}
