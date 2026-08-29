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
import java.util.concurrent.atomic.AtomicInteger;
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
import org.samlier.core.evaluation.EvidenceRef;
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
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class CaseExecutionServiceTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @TempDir java.nio.file.Path directory;
    private SqliteCaseExecutionRepository repository;
    private CaseExecutionService service;
    private CaseContext context;

    @BeforeEach
    void setUp() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plan = plan();
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN_ID, plan.id(), RunStatus.RUNNING, Reachability.UNKNOWN, Map.of(), NOW, NOW));
        var transcript = new FileTranscriptRecorder(database, json, directory);
        repository = new SqliteCaseExecutionRepository(database, json);
        service = new CaseExecutionService(repository);
        context = context(plan.parameters(), transcript);
    }

    @Test
    void persistsWaitingStateAndOutboxBeforeReturning() {
        var starts = new AtomicInteger();
        var testCase = waitingCase("case-outbox", starts);

        var execution = service.start(RUN_ID, testCase, context);
        var repeated = service.start(RUN_ID, testCase, context);

        assertEquals(CaseExecutionStatus.WAITING_INBOUND, execution.status());
        assertEquals(NOW.plus(Duration.ofMinutes(5)), execution.waitCondition().expiresAt());
        assertEquals(execution, repeated);
        assertEquals(1, starts.get());
        assertEquals(1, repository.listOutbox(RUN_ID).size());
    }

    @Test
    void resumesToAnOutcomeWithoutAllowingCaseSideVerdicts() {
        var testCase = waitingCase("case-finish", new AtomicInteger());
        service.start(RUN_ID, testCase, context);

        var finished = service.resume(
                RUN_ID, testCase, context, new CaseEvent.InboundMessage(
                        "<Response/>".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        new EvidenceRef("transcript", "transcript:response")));

        assertEquals(CaseExecutionStatus.FINISHED, finished.status());
        assertEquals(Outcome.SATISFIED, finished.outcome().outcome());
        assertEquals(finished, service.resume(
                RUN_ID, testCase, context, new CaseEvent.InboundMessage(
                        "<Response/>".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        new EvidenceRef("transcript", "transcript:response"))));
    }

    @Test
    void rejectsNondeterministicActionIdsBeforeWritingAnything() {
        var testCase = new TestCase() {
            @Override public String id() { return "case-random-action"; }
            @Override public TargetRole role() { return TargetRole.IDP; }

            @Override
            public CaseStep start(CaseContext ignored) {
                return new CaseStep.Continue(
                        new CaseState("send", Map.of()),
                        List.of(new OutboundAction(
                                "random", OutboundKind.AUTHN_REQUEST, new byte[] {1},
                                URI.create("https://idp.example/sso"), false)));
            }

            @Override
            public CaseStep resume(CaseContext ignored, CaseState state, CaseEvent event) {
                throw new UnsupportedOperationException();
            }
        };

        assertThrows(IllegalArgumentException.class, () -> service.start(RUN_ID, testCase, context));
        assertEquals(List.of(), repository.listOutbox(RUN_ID));
    }

    @Test
    void rejectsAnEventThatDoesNotMatchThePersistedWaitState() {
        var testCase = waitingCase("case-event-type", new AtomicInteger());
        service.start(RUN_ID, testCase, context);

        assertThrows(IllegalArgumentException.class, () -> service.resume(
                RUN_ID, testCase, context, new CaseEvent.ConfigConfirmed()));
        assertEquals(CaseExecutionStatus.WAITING_INBOUND,
                repository.find(RUN_ID, testCase.id()).orElseThrow().status());
    }

    @Test
    void rejectsACaseDesignedForAnotherTargetRoleBeforeStartingIt() {
        var testCase = waitingCase("case-wrong-role", new AtomicInteger(), TargetRole.SP);

        assertThrows(IllegalArgumentException.class, () -> service.start(RUN_ID, testCase, context));
        assertEquals(List.of(), repository.listOutbox(RUN_ID));
    }

    private TestCase waitingCase(String caseId, AtomicInteger starts) {
        return waitingCase(caseId, starts, TargetRole.IDP);
    }

    private TestCase waitingCase(String caseId, AtomicInteger starts, TargetRole role) {
        return new TestCase() {
            @Override public String id() { return caseId; }
            @Override public TargetRole role() { return role; }

            @Override
            public CaseStep start(CaseContext ignored) {
                starts.incrementAndGet();
                var next = new CaseState("await-response", Map.of("sequence", 0));
                var action = new OutboundAction(
                        ActionIds.derive(RUN_ID, caseId, next.phase(), 0),
                        OutboundKind.AUTHN_REQUEST,
                        "<AuthnRequest/>".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        URI.create("https://idp.example/sso"),
                        false);
                return new CaseStep.AwaitInbound(
                        next, List.of(action), new InboundMatcher("saml-response", Map.of()), Duration.ofMinutes(5));
            }

            @Override
            public CaseStep resume(CaseContext ignored, CaseState state, CaseEvent event) {
                return new CaseStep.Finish(CaseOutcome.of(Outcome.SATISFIED, "response.accepted", List.of()));
            }
        };
    }

    private CaseContext context(TestPlan.Parameters parameters, TranscriptRecorder transcript) {
        return new CaseContext() {
            @Override public String runId() { return RUN_ID; }
            @Override public TargetRole targetRole() { return TargetRole.IDP; }
            @Override public Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
            @Override public TestPlan.Parameters parameters() { return parameters; }
            @Override public Reachability reachability() { return Reachability.UNKNOWN; }
            @Override public TranscriptRecorder transcript() { return transcript; }
            @Override public boolean transcriptComplete() { return false; }
        };
    }

    private TestPlan plan() {
        return new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS",
                "Case execution test",
                PlanProfile.IDP_CORE,
                new TestPlan.Target(
                        TargetKind.IDP,
                        "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL,
                Map.of(),
                TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(),
                NOW,
                NOW);
    }
}
