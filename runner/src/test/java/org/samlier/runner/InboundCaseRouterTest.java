package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;

class InboundCaseRouterTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    @TempDir java.nio.file.Path directory;
    private SqliteCaseExecutionRepository repository;
    private CaseExecutionService executions;
    private DefaultCaseContext context;

    @BeforeEach
    void setUp() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var plan = plan();
        new SqlitePlanRepository(database, json).save(plan);
        new SqliteRunRepository(database, json).save(new TestRun(
                RUN_ID, plan.id(), RunStatus.RUNNING, Reachability.CONFIRMED, Map.of(), NOW, NOW));
        repository = new SqliteCaseExecutionRepository(database, json);
        executions = new CaseExecutionService(repository);
        context = new DefaultCaseContext(
                RUN_ID, TargetRole.IDP, Clock.fixed(NOW, ZoneOffset.UTC), plan.parameters(),
                plan.interaction(),
                Reachability.CONFIRMED, new FileTranscriptRecorder(database, json, directory), false);
    }

    @Test
    void resumesTheSingleCaseWhosePersistedCriteriaMatch() {
        var first = inboundCase("IIP-G03-a-idp-01", "_request-one");
        var second = inboundCase("IIP-SSO01-aa-idp-01", "_request-two");
        executions.start(RUN_ID, first, context);
        executions.start(RUN_ID, second, context);
        var router = new InboundCaseRouter(
                repository, new TestCaseRegistry(List.of(first, second)), executions);

        var routed = router.route(
                RUN_ID, "saml-response", Map.of("InResponseTo", "_request-two"),
                "<Response/>".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new EvidenceRef("transcript", "tx_two"), context).orElseThrow();

        assertEquals("IIP-SSO01-aa-idp-01", routed.caseId());
        assertEquals(CaseExecutionStatus.FINISHED, routed.status());
        assertEquals(Outcome.SATISFIED, routed.outcome().outcome());
        assertEquals(CaseExecutionStatus.WAITING_INBOUND,
                repository.find(RUN_ID, first.id()).orElseThrow().status());
    }

    @Test
    void returnsEmptyForAnUnsolicitedMessageAndFailsClosedOnAmbiguity() {
        var first = inboundCase("IIP-G03-a-idp-01", "_same");
        var second = inboundCase("IIP-SSO01-aa-idp-01", "_same");
        executions.start(RUN_ID, first, context);
        executions.start(RUN_ID, second, context);
        var router = new InboundCaseRouter(
                repository, new TestCaseRegistry(List.of(first, second)), executions);

        assertTrue(router.route(
                RUN_ID, "saml-response", Map.of("InResponseTo", "_other"), new byte[] {1},
                new EvidenceRef("transcript", "tx_other"), context).isEmpty());
        assertThrows(IllegalStateException.class, () -> router.route(
                RUN_ID, "saml-response", Map.of("InResponseTo", "_same"), new byte[] {1},
                new EvidenceRef("transcript", "tx_same"), context));
    }

    @Test
    void requiresEveryPersistedCriterionInsteadOfMatchingOnlyOne() {
        var testCase = new AwaitingCase(
                "IIP-G03-a-idp-01", Map.of("InResponseTo", "_one", "Destination", "https://suite.example/acs"));
        executions.start(RUN_ID, testCase, context);
        var router = new InboundCaseRouter(repository, new TestCaseRegistry(List.of(testCase)), executions);

        assertTrue(router.route(
                RUN_ID, "saml-response", Map.of("InResponseTo", "_one"), new byte[] {1},
                new EvidenceRef("transcript", "tx_partial"), context).isEmpty());
    }

    private TestCase inboundCase(String id, String requestId) {
        return new AwaitingCase(id, Map.of("InResponseTo", requestId));
    }

    private final class AwaitingCase implements TestCase {
        private final String id;
        private final Map<String, String> criteria;
        private AwaitingCase(String id, Map<String, String> criteria) { this.id = id; this.criteria = criteria; }
        @Override public String id() { return id; }
        @Override public TargetRole role() { return TargetRole.IDP; }
        @Override public CaseStep start(CaseContext ignored) {
            return new CaseStep.AwaitInbound(
                    new CaseState("waiting", Map.of()), List.of(),
                    new InboundMatcher("saml-response", criteria), Duration.ofMinutes(2));
        }
        @Override public CaseStep resume(CaseContext ignored, CaseState state, CaseEvent event) {
            if (!(event instanceof CaseEvent.InboundMessage inbound)) throw new IllegalArgumentException();
            return new CaseStep.Finish(CaseOutcome.of(
                    Outcome.SATISFIED, "matched", List.of(inbound.evidence())));
        }
    }

    private TestPlan plan() {
        return new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Inbound routing", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), NOW, NOW);
    }
}
