package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.ApplicabilityInput;
import org.samlier.core.evaluation.ApplicabilityInputRepository;
import org.samlier.core.evaluation.PredicateCatalog;
import org.samlier.core.evaluation.PredicateCatalog.ConflictPolicy;
import org.samlier.core.evaluation.PredicateKind;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;

class PersistedApplicabilityInputProviderTest {
    private static final Instant NOW = Instant.parse("2026-08-29T13:00:00Z");

    @Test
    void returnsPersistedInputAndTreatsAbsenceAsUnknown() {
        var stored = new AtomicReference<ApplicabilityInput>();
        ApplicabilityInputRepository repository = repository(stored);
        var provider = new PersistedApplicabilityInputProvider(repository);

        assertEquals(new ApplicabilityInput(null, null, List.of(), null),
                provider.input(definition(), run(), plan()));

        var observed = new ApplicabilityInput(false, true, List.of("transcript:1"), null);
        stored.set(observed);
        assertEquals(observed, provider.input(definition(), run(), plan()));
    }

    @Test
    void rejectsRunAndPlanFromDifferentScopes() {
        var provider = new PersistedApplicabilityInputProvider(repository(new AtomicReference<>()));
        var otherRun = new TestRun(
                "run_0123456789ABCDEFGHJKMNPQRS", "plan_other_123456789ABCDEFGHJKMNPQ",
                RunStatus.RUNNING, Reachability.CONFIRMED, Map.of(), NOW, NOW);
        assertThrows(IllegalArgumentException.class, () -> provider.input(definition(), otherRun, plan()));
    }

    private ApplicabilityInputRepository repository(AtomicReference<ApplicabilityInput> stored) {
        return new ApplicabilityInputRepository() {
            @Override
            public Optional<ApplicabilityInput> find(String runId, String predicate) {
                return Optional.ofNullable(stored.get());
            }

            @Override
            public void save(String runId, String predicate, ApplicabilityInput input, Instant updatedAt) {
                stored.set(input);
            }
        };
    }

    private PredicateCatalog.Definition definition() {
        return new PredicateCatalog.Definition(
                "feature", PredicateKind.CAPABILITY_BASED, List.of("declared.feature"),
                List.of("observed.feature"), ConflictPolicy.INCONSISTENT, null);
    }

    private TestPlan plan() {
        return new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Applicability persistence", PlanProfile.IDP_CORE,
                new TestPlan.Target(
                        TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), NOW, NOW);
    }

    private TestRun run() {
        return new TestRun(
                "run_0123456789ABCDEFGHJKMNPQRS", "plan_0123456789ABCDEFGHJKMNPQRS",
                RunStatus.RUNNING, Reachability.CONFIRMED, Map.of(), NOW, NOW);
    }
}
