package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class CaseRunProjectionTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    @TempDir java.nio.file.Path directory;
    private SqliteCaseExecutionRepository repository;

    @BeforeEach
    void setUp() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plan = new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Projection", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), NOW, NOW);
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN_ID, plan.id(), RunStatus.RUNNING, Reachability.UNKNOWN, Map.of(), NOW, NOW));
        repository = new SqliteCaseExecutionRepository(database, json);
    }

    @Test
    void projectsFinishedAndPendingRegisteredCasesInStableOrder() {
        persist(finished("IIP-SSO01-aa-idp-01", Outcome.SATISFIED));
        persist(running("IIP-SSO01-ab-idp-01"));
        persist(finished("IIP-G03-a-idp-01", Outcome.VIOLATED));
        var projection = new CaseRunProjection(repository, registry(
                "IIP-SSO01-aa-idp-01", "IIP-SSO01-ab-idp-01", "IIP-G03-a-idp-01"));

        var result = projection.completed(RUN_ID);

        assertEquals(List.of("IIP-G03-a-idp-01", "IIP-SSO01-aa-idp-01", "IIP-SSO01-ab-idp-01"),
                result.stream().map(value -> value.id()).toList());
        assertEquals(List.of("IIP-G03.a", "IIP-SSO01.aa", "IIP-SSO01.ab"),
                result.stream().map(value -> value.obligationKey()).toList());
        assertEquals(List.of(Outcome.VIOLATED, Outcome.SATISFIED, Outcome.NOT_VERIFIED),
                result.stream().map(value -> value.outcome().outcome()).toList());
        assertEquals("case_in_progress", result.get(2).outcome().notVerifiedReason());
    }

    @Test
    void failsClosedWhenPersistenceContainsAnUnregisteredCase() {
        persist(finished("IIP-G03-a-idp-01", Outcome.SATISFIED));

        assertThrows(IllegalArgumentException.class,
                () -> new CaseRunProjection(repository, registry()).completed(RUN_ID));
    }

    private void persist(CaseExecution execution) {
        repository.apply(-1, execution, List.of());
    }

    private CaseExecution finished(String id, Outcome outcome) {
        return new CaseExecution(RUN_ID, id, 0, CaseExecutionStatus.FINISHED,
                CaseState.initial(), null, CaseOutcome.of(outcome, "test", List.of()), NOW);
    }

    private CaseExecution running(String id) {
        return new CaseExecution(RUN_ID, id, 0, CaseExecutionStatus.RUNNING,
                new CaseState("running", Map.of()), null, null, NOW);
    }

    private TestCaseRegistry registry(String... ids) {
        return new TestCaseRegistry(java.util.Arrays.stream(ids).map(id -> new TestCase() {
            @Override public String id() { return id; }
            @Override public TargetRole role() { return TargetRole.IDP; }
            @Override public org.samlier.core.caseexec.CaseStep start(
                    org.samlier.core.caseexec.CaseContext context) { throw new UnsupportedOperationException(); }
            @Override public org.samlier.core.caseexec.CaseStep resume(
                    org.samlier.core.caseexec.CaseContext context,
                    org.samlier.core.caseexec.CaseState state,
                    org.samlier.core.caseexec.CaseEvent event) { throw new UnsupportedOperationException(); }
        }).toList());
    }
}
