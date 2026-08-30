package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.samlier.core.casedef.CaseDefinitionCatalog;
import org.samlier.core.casedef.CaseDefinitionCatalog.CaseDefinition;
import org.samlier.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import org.samlier.core.casedef.CaseDefinitionCatalog.Milestone;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.ConfigurationFailureSemantics;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboxEntry;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;
import org.samlier.runner.RunCampaignQuery.EvidenceClass;
import org.samlier.runner.RunCampaignQuery.Plan;
import org.samlier.runner.cases.AttestationOption;
import org.samlier.runner.cases.AttestedOutcomeTestCase;
import org.samlier.runner.cases.ProtocolEvidenceCase;

class RunCampaignServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    @Test
    void sharesOneMetadataRefreshActionAcrossMultipleCases() {
        var first = definition("IIP-MD04-a-idp-01", "IIP-MD04.a", ExecutionMode.CONFIG, "none");
        var second = definition("IIP-MD05-a-idp-01", "IIP-MD05.a", ExecutionMode.CONFIG, "none");
        var firstCase = new ProtocolCase(first.id());
        var secondCase = new ProtocolCase(second.id());
        var report = service(
                List.of(first, second), List.of(firstCase, secondCase),
                List.of(execution(first.id(), false), execution(second.id(), false))).report("run");

        var campaign = report.campaigns().get(0);
        assertEquals("operator_assisted-metadata_refresh-shared-metadata-refresh", campaign.id());
        assertEquals(2, campaign.caseIds().size());
        assertEquals(1, campaign.deliberateUserActions());
        assertEquals(1, campaign.remainingUserActions());
        assertEquals(2, report.casesByEvidenceClass().get(EvidenceClass.OPERATOR_ASSISTED));
    }

    @Test
    void countsTheUnionOfMetadataFixtureOperationsRatherThanCases() {
        var first = definition("first", "IIP-MD03.a", ExecutionMode.CONFIG, "none");
        var second = definition("second", "IIP-MD03.b", ExecutionMode.CONFIG, "none");
        var report = service(
                List.of(first, second),
                List.of(
                        new CampaignProtocolCase(first.id(), List.of("control", "unsigned", "bad-signature")),
                        new CampaignProtocolCase(second.id(), List.of("control", "bad-signature", "other-key"))),
                List.of(execution(first.id(), false), execution(second.id(), false))).report("run");

        var campaign = report.campaigns().get(0);
        assertEquals(4, campaign.deliberateUserActions());
        assertEquals(4, campaign.remainingUserActions());
    }

    @Test
    void keepsFreshSessionBoundaryAndDoesNotTurnMissingEvidenceIntoAnOutcome() {
        var definition = definition(
                "IIP-IDP05-a-idp-01", "IIP-IDP05.a", ExecutionMode.BROWSER, "required");
        var testCase = new ProtocolCase(definition.id());
        var execution = execution(definition.id(), false);
        var report = service(List.of(definition), List.of(testCase), List.of(execution)).report("run");

        assertTrue(report.campaigns().get(0).freshSessionRequired());
        assertEquals(List.of("correlated-saml-response"),
                report.campaigns().get(0).expectedTranscriptEvidence());
        assertEquals(1, report.notVerifiedCases());
        assertEquals(CaseExecutionStatus.RUNNING, execution.status());
        assertEquals(null, execution.outcome());
    }

    @Test
    void reportsSelfAttestationSeparatelyAndKeepsAllPlanBudgetsBounded() {
        var automatic = definition("IIP-G03-a-idp-01", "IIP-G03.a", ExecutionMode.AUTOMATED, "none");
        var browser = definition("IIP-IDP04-a-idp-01", "IIP-IDP04.a", ExecutionMode.BROWSER, "none");
        var attested = definition("IIP-G02-c-idp-01", "IIP-G02.c", ExecutionMode.ATTESTED, "none");
        var report = service(
                List.of(automatic, browser, attested),
                List.of(
                        passive(automatic.id()), passive(browser.id()),
                        new AttestedOutcomeTestCase(
                                attested.id(), TargetRole.IDP, "attestation", "Review evidence.",
                                java.time.Duration.ofHours(1),
                                List.of(AttestationOption.of("satisfied", Outcome.SATISFIED, "ok")))),
                List.of(execution(automatic.id(), true), execution(browser.id(), false), execution(attested.id(), true)))
                .report("run");

        assertEquals(1, report.externallyVerifiedCases());
        assertEquals(1, report.selfAttestedCases());
        assertEquals(1, report.notVerifiedCases());
        assertEquals(List.of(Plan.QUICK, Plan.STANDARD, Plan.FULL),
                report.plans().stream().map(RunCampaignQuery.PlanSummary::plan).toList());
        assertTrue(report.plans().stream().allMatch(RunCampaignQuery.PlanSummary::budgetMet));
        assertEquals(1, report.plans().get(2).selfAttestationSections());
        assertFalse(report.campaigns().stream()
                .filter(value -> value.evidenceClass() == EvidenceClass.SELF_ATTESTED)
                .findFirst().orElseThrow().caseIds().isEmpty());
    }

    @Test
    void classifiesFinishedAutomatedExecutionsWithoutAnInteractiveRegistryEntry() {
        var automatic = definition("IIP-G03-a-idp-01", "IIP-G03.a", ExecutionMode.AUTOMATED, "none");
        var report = service(
                List.of(automatic), List.of(), List.of(execution(automatic.id(), true))).report("run");

        assertEquals(1, report.externallyVerifiedCases());
        assertEquals(EvidenceClass.PROTOCOL_OBSERVED, report.classifications().get(0).evidenceClass());
        assertEquals(0, report.plans().get(0).deliberateUserActions());
    }

    @Test
    void groupsTheAutomaticallyChainedActiveProbeAndKeepsFreshSessionBoundary() {
        var first = definition("IIP-IDP05-a-idp-01", "IIP-IDP05.a", ExecutionMode.BROWSER, "required");
        var second = definition("IIP-IDP07-a-idp-01", "IIP-IDP07.a", ExecutionMode.BROWSER, "required");
        var report = service(
                List.of(first, second),
                List.of(new BrowserScenarioCase(first.id()), new BrowserScenarioCase(second.id())),
                List.of(execution(first.id(), false), execution(second.id(), false))).report("run");

        var campaign = report.campaigns().get(0);
        assertEquals(EvidenceClass.PROTOCOL_OBSERVED, campaign.evidenceClass());
        assertEquals("protocol_observed-login-shared-active-probe-chain", campaign.id());
        assertEquals(1, campaign.deliberateUserActions());
        assertEquals(1, campaign.remainingUserActions());
        assertTrue(campaign.freshSessionRequired());
    }

    private RunCampaignService service(
            List<CaseDefinition> definitions, List<TestCase> cases, List<CaseExecution> executions) {
        return new RunCampaignService(
                new MemoryExecutions(executions), new CaseDefinitionCatalog(definitions),
                new TestCaseRegistry(cases), ignored -> context());
    }

    private CaseDefinition definition(String id, String obligation, ExecutionMode mode, String session) {
        return new CaseDefinition(
                id, obligation, TargetRole.IDP, mode, Milestone.M1,
                List.of(), Map.of(), List.of(), List.of(), List.of(), "counterexample",
                List.of(), new CaseDefinitionCatalog.Requirements(List.of(), session), false,
                mode == ExecutionMode.CONFIG ? ConfigurationFailureSemantics.TEST_PRECONDITION : null,
                "sha256:" + "0".repeat(64));
    }

    private CaseExecution execution(String id, boolean finished) {
        return new CaseExecution(
                "run", id, 0, finished ? CaseExecutionStatus.FINISHED : CaseExecutionStatus.RUNNING,
                CaseState.initial(), null,
                finished ? CaseOutcome.of(Outcome.SATISFIED, "observed", List.of()) : null, NOW);
    }

    private TestCase passive(String id) {
        return new TestCase() {
            @Override public String id() { return id; }
            @Override public TargetRole role() { return TargetRole.IDP; }
            @Override public CaseStep start(CaseContext context) { throw new UnsupportedOperationException(); }
            @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private CaseContext context() {
        return new CaseContext() {
            @Override public String runId() { return "run"; }
            @Override public TargetRole targetRole() { return TargetRole.IDP; }
            @Override public Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
            @Override public org.samlier.core.plan.TestPlan.Parameters parameters() {
                return org.samlier.core.plan.TestPlan.Parameters.defaults();
            }
            @Override public org.samlier.core.plan.TestPlan.Interaction interaction() {
                return org.samlier.core.plan.TestPlan.Interaction.defaults();
            }
            @Override public org.samlier.core.run.Reachability reachability() {
                return org.samlier.core.run.Reachability.CONFIRMED;
            }
            @Override public org.samlier.core.transcript.TranscriptRecorder transcript() { return null; }
            @Override public boolean transcriptComplete() { return true; }
        };
    }

    private static final class ProtocolCase implements TestCase, ProtocolEvidenceCase {
        private final String id;
        private ProtocolCase(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public TargetRole role() { return TargetRole.IDP; }
        @Override public CaseStep start(CaseContext context) { throw new UnsupportedOperationException(); }
        @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
            throw new UnsupportedOperationException();
        }
        @Override public EvidenceStatus evidenceStatus(CaseContext context) {
            return new EvidenceStatus(
                    false, List.of("correlated-saml-response"), List.of(), Map.of());
        }
    }

    private static final class BrowserScenarioCase
            implements TestCase, BrowserFrontChannelScenario, org.samlier.runner.cases.BrowserPrompt {
        private final String id;
        private BrowserScenarioCase(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public TargetRole role() { return TargetRole.IDP; }
        @Override public String browserInstructionsEn() { return "Complete the scenario."; }
        @Override public String instructionsEn(CaseState state) { return browserInstructionsEn(); }
        @Override public boolean requiresFreshSession(CaseState state) { return true; }
        @Override public CaseStep start(CaseContext context) { throw new UnsupportedOperationException(); }
        @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CampaignProtocolCase
            implements TestCase, ProtocolEvidenceCase, EvidenceCampaignCase {
        private final String id;
        private final List<String> actionKeys;
        private CampaignProtocolCase(String id, List<String> actionKeys) {
            this.id = id;
            this.actionKeys = actionKeys;
        }
        @Override public String id() { return id; }
        @Override public TargetRole role() { return TargetRole.IDP; }
        @Override public String evidenceCampaignId() { return "metadata-fixture-refresh"; }
        @Override public String evidenceCampaignTitle() { return "Refresh metadata fixtures"; }
        @Override public RunCampaignQuery.ActionKind evidenceActionKind() {
            return RunCampaignQuery.ActionKind.METADATA_REFRESH;
        }
        @Override public List<String> evidenceActionKeys() { return actionKeys; }
        @Override public CaseStep start(CaseContext context) { throw new UnsupportedOperationException(); }
        @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
            throw new UnsupportedOperationException();
        }
        @Override public EvidenceStatus evidenceStatus(CaseContext context) {
            return new EvidenceStatus(false, List.of(), List.of(), Map.of());
        }
    }

    private record MemoryExecutions(List<CaseExecution> values) implements CaseExecutionRepository {
        @Override public Optional<CaseExecution> find(String runId, String caseId) { return Optional.empty(); }
        @Override public List<CaseExecution> list(String runId) { return values; }
        @Override public boolean apply(long expectedRevision, CaseExecution execution, List<OutboundAction> actions) {
            throw new UnsupportedOperationException();
        }
        @Override public List<OutboxEntry> listOutbox(String runId) { return List.of(); }
        @Override public Optional<OutboxEntry> findOutbox(String actionId) { return Optional.empty(); }
        @Override public boolean transitionOutbox(
                String actionId, OutboxStatus expected, OutboxStatus next, Map<String, Object> sendResult,
                String transcriptEntryId, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }
        @Override public int recoverSendingAsUnknownDelivery(Instant updatedAt) { return 0; }
    }
}
