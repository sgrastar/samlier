package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboxEntry;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.casedef.CaseDefinitionCatalog;
import org.samlier.core.casedef.CaseDefinitionCatalog.CaseDefinition;
import org.samlier.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import org.samlier.core.casedef.CaseDefinitionCatalog.Milestone;
import org.samlier.core.casedef.CaseDefinitionCatalog.Requirements;
import org.samlier.core.evaluation.ApplicabilityEvaluation;
import org.samlier.core.evaluation.CoverageCatalog;
import org.samlier.core.evaluation.PredicateKind;
import org.samlier.core.evaluation.Rfc2119Level;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.cases.ApprovedAttestedCaseRegistry;

class ApprovedCaseStarterTest {
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";

    @Test
    void startsOnlyProfileSelectedCasesWhoseApplicabilityIsTrue() {
        var definitions = definitions();
        var registry = ApprovedAttestedCaseRegistry.create(definitions);
        var plan = plan(PlanProfile.IDP_FULL);
        var run = run(plan);

        var falseRepository = new MemoryRepository();
        var falseStarter = new ApprovedCaseStarter(
                coverage(), definitions, registry, new CaseExecutionService(falseRepository),
                (ignoredRun, ignoredPlan) -> List.of(applicability(false)));
        var falseStarted = falseStarter.startApplicable(run, plan, context(plan));
        assertEquals(List.of("IIP-A-a-idp-01"), falseStarted.stream().map(CaseExecution::caseId).toList());

        var trueRepository = new MemoryRepository();
        var trueStarter = new ApprovedCaseStarter(
                coverage(), definitions, registry, new CaseExecutionService(trueRepository),
                (ignoredRun, ignoredPlan) -> List.of(applicability(true)));
        var trueStarted = trueStarter.startApplicable(run, plan, context(plan));
        assertEquals(List.of("IIP-A-a-idp-01", "IIP-B-b-idp-01"),
                trueStarted.stream().map(CaseExecution::caseId).toList());

        var corePlan = plan(PlanProfile.IDP_CORE);
        var coreRun = run(corePlan);
        var coreRepository = new MemoryRepository();
        var coreStarter = new ApprovedCaseStarter(
                coverage(), definitions, registry, new CaseExecutionService(coreRepository),
                (ignoredRun, ignoredPlan) -> List.of());
        assertEquals(List.of("IIP-A-a-idp-01"), coreStarter.startApplicable(
                coreRun, corePlan, context(corePlan)).stream().map(CaseExecution::caseId).toList());
    }

    private CoverageCatalog coverage() {
        return new CoverageCatalog(List.of(
                new CoverageCatalog.Obligation(
                        "IIP-A.a", "IIP-A", Rfc2119Level.MUST, List.of(TargetRole.IDP), null,
                        CoverageCatalog.Testability.ATTESTED, CoverageCatalog.ProfileScope.CORE),
                new CoverageCatalog.Obligation(
                        "IIP-B.b", "IIP-B", Rfc2119Level.MUST, List.of(TargetRole.IDP), "feature",
                        CoverageCatalog.Testability.ATTESTED, CoverageCatalog.ProfileScope.FULL)));
    }

    private CaseDefinitionCatalog definitions() {
        return new CaseDefinitionCatalog(List.of(
                definition("IIP-A-a-idp-01", "IIP-A.a"),
                definition("IIP-B-b-idp-01", "IIP-B.b")));
    }

    private CaseDefinition definition(String id, String obligation) {
        return new CaseDefinition(
                id, obligation, TargetRole.IDP, ExecutionMode.ATTESTED, Milestone.M1,
                List.of(), Map.of(), List.of(), List.of(), List.of(),
                "A declaration-only oracle would miss the target behavior.", List.of(),
                new Requirements(List.of(), "none"), false, null, "sha256:" + "a".repeat(64));
    }

    private ApplicabilityEvaluation applicability(boolean value) {
        return new ApplicabilityEvaluation(
                "IIP-B.b", "feature", PredicateKind.CLAIM_BASED, value, null,
                value ? ApplicabilityEvaluation.EffectiveResult.TRUE
                        : ApplicabilityEvaluation.EffectiveResult.FALSE,
                false, ApplicabilityEvaluation.Basis.DECLARED, List.of(), null);
    }

    private TestPlan plan(PlanProfile profile) {
        return new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "Attested", profile,
                new TestPlan.Target(
                        TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                new TestPlan.Interaction(true, true), NOW, NOW);
    }

    private TestRun run(TestPlan plan) {
        return new TestRun(RUN_ID, plan.id(), RunStatus.COMPLETED, Reachability.CONFIRMED, Map.of(), NOW, NOW);
    }

    private DefaultCaseContext context(TestPlan plan) {
        return new DefaultCaseContext(
                RUN_ID, TargetRole.IDP, Clock.fixed(NOW, ZoneOffset.UTC), plan.parameters(), plan.interaction(),
                Reachability.CONFIRMED, new NoopTranscript(), true);
    }

    private static final class MemoryRepository implements CaseExecutionRepository {
        private final Map<String, CaseExecution> values = new LinkedHashMap<>();
        @Override public Optional<CaseExecution> find(String runId, String caseId) {
            return Optional.ofNullable(values.get(runId + "\n" + caseId));
        }
        @Override public List<CaseExecution> list(String runId) {
            return values.values().stream().filter(value -> value.runId().equals(runId))
                    .sorted(java.util.Comparator.comparing(CaseExecution::caseId)).toList();
        }
        @Override public boolean apply(long expectedRevision, CaseExecution execution, List<OutboundAction> actions) {
            var key = execution.runId() + "\n" + execution.caseId();
            var current = values.get(key);
            if ((current == null ? -1 : current.revision()) != expectedRevision) return false;
            values.put(key, execution);
            return true;
        }
        @Override public List<OutboxEntry> listOutbox(String runId) { return List.of(); }
        @Override public Optional<OutboxEntry> findOutbox(String actionId) { return Optional.empty(); }
        @Override public boolean transitionOutbox(
                String actionId, OutboxStatus expected, OutboxStatus next, Map<String, Object> sendResult,
                String transcriptEntryId, Instant updatedAt) { return false; }
        @Override public int recoverSendingAsUnknownDelivery(Instant updatedAt) { return 0; }
    }

    private static final class NoopTranscript implements TranscriptRecorder {
        @Override public TranscriptEntry record(TranscriptInput input) { throw new UnsupportedOperationException(); }
        @Override public TranscriptEntry updateSamlAnalysis(
                String entryId, String correlationId, Map<String, Object> samlSummary) {
            throw new UnsupportedOperationException();
        }
        @Override public List<TranscriptEntry> list(String runId) { return new ArrayList<>(); }
    }
}
