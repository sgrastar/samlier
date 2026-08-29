package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.caseexec.ActionIds;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.InboundMatcher;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboundKind;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.cases.IdpErrorResponseTestCase;
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class AutomatedCaseRunnerTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    @TempDir java.nio.file.Path directory;
    private SqliteCaseExecutionRepository repository;
    private AutomatedCaseRunner runner;
    private TestPlan plan;
    private TranscriptRecorder transcript;

    @BeforeEach
    void setUp() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        plan = plan();
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN_ID, plan.id(), RunStatus.RUNNING, Reachability.UNKNOWN, Map.of(), NOW, NOW));
        transcript = new FileTranscriptRecorder(database, json, directory);
        repository = new SqliteCaseExecutionRepository(database, json);
        runner = new AutomatedCaseRunner(
                new TestCaseRegistry(List.of(activeCase(), passiveCase())),
                new CaseExecutionService(repository));
    }

    @Test
    void startsActiveCasesDuringTheRunAndPassiveCasesOnlyAfterTranscriptCompletion() {
        var during = runner.startReady(RUN_ID, PlanProfile.IDP_CORE, context(false));
        assertEquals(List.of(IdpErrorResponseTestCase.CASE_ID), during.stream().map(value -> value.caseId()).toList());
        assertEquals(CaseExecutionStatus.WAITING_INBOUND, during.getFirst().status());
        assertEquals(1, repository.listOutbox(RUN_ID).size());

        var completed = runner.startReady(RUN_ID, PlanProfile.IDP_CORE, context(true));
        assertEquals(2, completed.size());
        assertEquals(CaseExecutionStatus.FINISHED,
                repository.find(RUN_ID, "IIP-G03-a-idp-01").orElseThrow().status());
        assertEquals(1, repository.listOutbox(RUN_ID).size());

        runner.startReady(RUN_ID, PlanProfile.IDP_CORE, context(true));
        assertEquals(1, repository.listOutbox(RUN_ID).size());
    }

    @Test
    void refusesAProfileForAnotherTargetRole() {
        assertThrows(IllegalArgumentException.class,
                () -> runner.startReady(RUN_ID, PlanProfile.SP_CORE, context(false)));
    }

    private TestCase activeCase() {
        return new TestCase() {
            @Override public String id() { return IdpErrorResponseTestCase.CASE_ID; }
            @Override public TargetRole role() { return TargetRole.IDP; }
            @Override public CaseStep start(CaseContext context) {
                var state = new CaseState("await-response", Map.of());
                return new CaseStep.AwaitInbound(
                        state,
                        List.of(new OutboundAction(
                                ActionIds.derive(RUN_ID, id(), state.phase(), 0), OutboundKind.AUTHN_REQUEST,
                                new byte[] {1}, URI.create("https://idp.example/sso"), false)),
                        new InboundMatcher("response", Map.of()), Duration.ofMinutes(2));
            }
            @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private TestCase passiveCase() {
        return new TestCase() {
            @Override public String id() { return "IIP-G03-a-idp-01"; }
            @Override public TargetRole role() { return TargetRole.IDP; }
            @Override public CaseStep start(CaseContext context) {
                if (!context.transcriptComplete()) throw new IllegalStateException("complete transcript required");
                return new CaseStep.Finish(CaseOutcome.of(Outcome.SATISFIED, "passive.satisfied", List.of()));
            }
            @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private CaseContext context(boolean complete) {
        return new CaseContext() {
            @Override public String runId() { return RUN_ID; }
            @Override public TargetRole targetRole() { return TargetRole.IDP; }
            @Override public Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
            @Override public TestPlan.Parameters parameters() { return plan.parameters(); }
            @Override public Reachability reachability() { return Reachability.UNKNOWN; }
            @Override public TranscriptRecorder transcript() { return transcript; }
            @Override public boolean transcriptComplete() { return complete; }
        };
    }

    private TestPlan plan() {
        return new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Automated case runner test", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(), TestPlan.Interaction.defaults(), NOW, NOW);
    }
}
