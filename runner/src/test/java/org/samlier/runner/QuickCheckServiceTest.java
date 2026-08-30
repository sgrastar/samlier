package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class QuickCheckServiceTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final String PLAN_ID = "plan_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    @TempDir java.nio.file.Path directory;
    private SqliteRunRepository runs;
    private QuickCheckService service;

    @BeforeEach
    void setUp() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plans = new SqlitePlanRepository(database, json);
        var plan = new TestPlan(
                PLAN_ID, "Quick check", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), NOW, NOW);
        plans.save(plan);
        runs = new SqliteRunRepository(database, json);
        var transcript = new FileTranscriptRecorder(database, json, directory);
        service = new QuickCheckService(
                plans, runs, transcript, transcript,
                new SqliteCaseExecutionRepository(database, json),
                new FilePlanKeyStore(directory, Clock.fixed(NOW, ZoneOffset.UTC)),
                (ignored, runId) -> java.util.List.of(),
                URI.create("https://suite.example"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void runsTheApprovedAutomatedSubsetButNeverCallsItConformance() {
        runs.save(run(RunStatus.COMPLETED));

        var result = service.execute(RUN_ID);

        assertEquals(QuickCheckService.DISCLAIMER, result.disclaimer());
        assertFalse(result.cases().isEmpty());
        assertFalse(result.cases().stream().anyMatch(value ->
                value.status() == CaseExecutionStatus.FINISHED
                        && value.outcome().outcome() == Outcome.VIOLATED));
        assertEquals(CaseExecutionStatus.FINISHED, result.cases().stream()
                .filter(value -> value.caseId().equals("IIP-IDP05-a-idp-01"))
                .findFirst().orElseThrow().status());
        assertEquals(Outcome.NOT_VERIFIED, result.cases().stream()
                .filter(value -> value.caseId().equals("IIP-IDP05-a-idp-01"))
                .findFirst().orElseThrow().outcome().outcome());
    }

    @Test
    void refusesToTreatAnIncompleteProtocolRunAsACompleteTranscript() {
        runs.save(run(RunStatus.RUNNING));

        assertThrows(IllegalArgumentException.class, () -> service.execute(RUN_ID));
    }

    @Test
    void repeatedExecutionReusesThePersistedCaseExecutions() {
        runs.save(run(RunStatus.COMPLETED));

        var first = service.execute(RUN_ID);
        var second = service.execute(RUN_ID);

        assertEquals(first.cases(), second.cases());
    }

    private TestRun run(RunStatus status) {
        return new TestRun(RUN_ID, PLAN_ID, status, Reachability.CONFIRMED, Map.of(), NOW, NOW);
    }
}
