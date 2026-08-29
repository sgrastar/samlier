package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.InboundMatcher;
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
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class CaseTimeoutServiceTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant START = Instant.parse("2026-08-29T00:00:00Z");
    @TempDir java.nio.file.Path directory;
    private SqliteCaseExecutionRepository repository;
    private CaseExecutionService executions;
    private TestPlan plan;
    private FileTranscriptRecorder transcript;

    @BeforeEach
    void setUp() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        plan = new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Timeout", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), START, START);
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN_ID, plan.id(), RunStatus.RUNNING, Reachability.UNKNOWN, Map.of(), START, START));
        repository = new SqliteCaseExecutionRepository(database, json);
        executions = new CaseExecutionService(repository);
        transcript = new FileTranscriptRecorder(database, json, directory);
    }

    @Test
    void expiresOnlyElapsedWaitsAndRecordsNotVerifiedThroughTheCase() {
        var elapsed = waitingCase("IIP-G03-a-idp-01", Duration.ofSeconds(10));
        var pending = waitingCase("IIP-SSO01-aa-idp-01", Duration.ofMinutes(5));
        executions.start(RUN_ID, elapsed, context(START));
        executions.start(RUN_ID, pending, context(START));
        var registry = new TestCaseRegistry(List.of(elapsed, pending));
        var timeouts = new CaseTimeoutService(repository, registry, executions);

        var result = timeouts.expireReady(RUN_ID, context(START.plusSeconds(10)));

        assertEquals(List.of("IIP-G03-a-idp-01"), result.stream().map(value -> value.caseId()).toList());
        assertEquals(CaseExecutionStatus.FINISHED, result.getFirst().status());
        assertEquals(Outcome.NOT_VERIFIED, result.getFirst().outcome().outcome());
        assertEquals(CaseExecutionStatus.WAITING_INBOUND,
                repository.find(RUN_ID, pending.id()).orElseThrow().status());
    }

    @Test
    void repeatedSweepIsIdempotent() {
        var testCase = waitingCase("IIP-G03-a-idp-01", Duration.ofSeconds(1));
        executions.start(RUN_ID, testCase, context(START));
        var timeouts = new CaseTimeoutService(
                repository, new TestCaseRegistry(List.of(testCase)), executions);

        assertEquals(1, timeouts.expireReady(RUN_ID, context(START.plusSeconds(2))).size());
        assertEquals(0, timeouts.expireReady(RUN_ID, context(START.plusSeconds(3))).size());
    }

    private TestCase waitingCase(String id, Duration ttl) {
        return new TestCase() {
            @Override public String id() { return id; }
            @Override public TargetRole role() { return TargetRole.IDP; }
            @Override public CaseStep start(CaseContext context) {
                return new CaseStep.AwaitInbound(
                        new CaseState("waiting", Map.of()), List.of(),
                        new InboundMatcher("saml-response", Map.of("InResponseTo", "_" + id)), ttl);
            }
            @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
                if (!(event instanceof CaseEvent.TimedOut timedOut)) throw new IllegalArgumentException();
                return new CaseStep.Finish(new CaseOutcome(
                        Outcome.NOT_VERIFIED, "timeout", "timeout", "case.timeout", List.of(),
                        Map.of("waited_seconds", timedOut.waited().toSeconds())));
            }
        };
    }

    private DefaultCaseContext context(Instant now) {
        return new DefaultCaseContext(
                RUN_ID, TargetRole.IDP, Clock.fixed(now, ZoneOffset.UTC), plan.parameters(),
                plan.interaction(),
                Reachability.UNKNOWN, transcript, false);
    }
}
