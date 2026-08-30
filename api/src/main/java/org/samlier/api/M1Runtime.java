package org.samlier.api;

import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import org.samlier.core.casedef.CaseDefinitionCatalogMapper;
import org.samlier.core.evaluation.CoverageCatalogMapper;
import org.samlier.core.evaluation.PredicateCatalogMapper;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.run.RunRepository;
import org.samlier.core.run.TestRun;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.CaseRunProjection;
import org.samlier.runner.ApprovedCaseStarter;
import org.samlier.runner.AttestationService;
import org.samlier.runner.ConfigurationService;
import org.samlier.runner.BrowserCompletionService;
import org.samlier.runner.BootstrapContractService;
import org.samlier.runner.CatalogApplicabilityProvider;
import org.samlier.runner.CaseExecutionService;
import org.samlier.runner.DefaultCaseContext;
import org.samlier.runner.OutboxIncidentProjection;
import org.samlier.runner.PendingInteractionService;
import org.samlier.runner.PersistedApplicabilityInputProvider;
import org.samlier.runner.ProtocolEvidenceAutomationService;
import org.samlier.runner.QuickCheckService;
import org.samlier.runner.RunEvaluationService;
import org.samlier.runner.access.RunAccessService;
import org.samlier.runner.cases.CachedTargetSigningCertificateProvider;
import org.samlier.runner.cases.ApprovedAttestedCaseRegistry;
import org.samlier.runner.cases.ApprovedConfigCaseRegistry;
import org.samlier.runner.cases.ApprovedBrowserCaseRegistry;
import org.samlier.runner.cases.M2AutomatedCaseRegistry;
import org.samlier.runner.cases.M3AutomatedCaseRegistry;
import org.samlier.runner.result.DefaultResultContextProvider;
import org.samlier.runner.result.EvaluationArtifactDigests;
import org.samlier.runner.result.ResultDocumentContext;
import org.samlier.runner.result.ResultJsonWriter;
import org.samlier.runner.result.ResultPublicationService;
import org.samlier.saml.crypto.FilePlanKeyStore;
import org.samlier.saml.metadata.TargetMetadataParser;
import org.samlier.store.FileRunArtifactRepository;
import org.samlier.store.JsonCodec;
import org.samlier.store.MetadataCache;
import org.samlier.store.SqliteApplicabilityInputRepository;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqliteRunAccessGrantRepository;
import org.samlier.store.SqlitePublicationRepository;

/** Phase 1 execution composition kept outside the HTTP root so its boundaries remain independently testable. */
final class M1Runtime {
    private final AppConfig config;
    private final QuickCheckService quickCheck;
    private final ResultPublicationService results;
    private final FileRunArtifactRepository artifacts;
    private final RunAccessService access;
    private final PlanRepository plans;
    private final RunRepository runs;
    private final TranscriptRecorder transcript;
    private final Clock clock;
    private final Map<org.samlier.core.casedef.CaseDefinitionCatalog.Milestone, List<ApprovedCaseStarter>> starters;
    private final PendingInteractionService pendingInteractions;
    private final BootstrapContractService bootstrapContracts;
    private final ProtocolEvidenceAutomationService protocolEvidence;
    private final AttestationService attestations;
    private final ConfigurationService configurations;
    private final BrowserCompletionService browserCompletions;
    private final SqliteCaseExecutionRepository caseExecutions;
    private final SqlitePublicationRepository publications;

    private M1Runtime(
            AppConfig config,
            QuickCheckService quickCheck,
            ResultPublicationService results,
            FileRunArtifactRepository artifacts,
            RunAccessService access,
            PlanRepository plans,
            RunRepository runs,
            TranscriptRecorder transcript,
            Clock clock,
            Map<org.samlier.core.casedef.CaseDefinitionCatalog.Milestone, List<ApprovedCaseStarter>> starters,
            PendingInteractionService pendingInteractions,
            BootstrapContractService bootstrapContracts,
            ProtocolEvidenceAutomationService protocolEvidence,
            AttestationService attestations,
            ConfigurationService configurations,
            BrowserCompletionService browserCompletions,
            SqliteCaseExecutionRepository caseExecutions,
            SqlitePublicationRepository publications) {
        this.config = config;
        this.quickCheck = quickCheck;
        this.results = results;
        this.artifacts = artifacts;
        this.access = access;
        this.plans = plans;
        this.runs = runs;
        this.transcript = transcript;
        this.clock = clock;
        this.starters = Map.copyOf(starters);
        this.pendingInteractions = pendingInteractions;
        this.bootstrapContracts = bootstrapContracts;
        this.protocolEvidence = protocolEvidence;
        this.attestations = attestations;
        this.configurations = configurations;
        this.browserCompletions = browserCompletions;
        this.caseExecutions = caseExecutions;
        this.publications = publications;
    }

    static M1Runtime create(
            AppConfig config,
            SqliteDatabase database,
            JsonCodec json,
            PlanRepository plans,
            RunRepository runs,
            TranscriptRecorder transcript,
            TranscriptContentReader transcriptContent,
            MetadataCache metadataCache,
            TargetMetadataParser metadataParser,
            FilePlanKeyStore keys,
            SqliteCaseExecutionRepository caseExecutions,
            org.samlier.runner.MetadataLabService metadataLab,
            Clock clock) {
        var documents = CatalogDocuments.load();
        var coverage = CoverageCatalogMapper.fromDocument(documents.parsed("tests/coverage.yaml"));
        var predicates = PredicateCatalogMapper.fromDocument(documents.parsed("tests/predicates.yaml"));
        var definitions = CaseDefinitionCatalogMapper.fromDocument(documents.parsed("tests/cases.yaml"));
        var targetCertificates = new CachedTargetSigningCertificateProvider(metadataCache, metadataParser);
        var quickCheck = new QuickCheckService(
                plans, runs, transcript, transcriptContent, caseExecutions, keys, targetCertificates,
                config.peerBaseUrl(), clock, definitions);
        var applicability = new CatalogApplicabilityProvider(
                coverage, predicates,
                new PersistedApplicabilityInputProvider(new SqliteApplicabilityInputRepository(database, json)));
        var m1Attested = ApprovedAttestedCaseRegistry.create(definitions);
        var m1Config = ApprovedConfigCaseRegistry.create(definitions);
        var m1Browser = ApprovedBrowserCaseRegistry.create(definitions, config.publicBaseUrl());
        var m2Attested = ApprovedAttestedCaseRegistry.create(
                definitions, org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M2);
        var m2Config = ApprovedConfigCaseRegistry.create(
                definitions, org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M2);
        var m2Browser = ApprovedBrowserCaseRegistry.create(
                definitions, config.publicBaseUrl(),
                org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M2);
        var m2Automated = M2AutomatedCaseRegistry.create(runId -> {
            var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
            try {
                return metadataCache.get(run.planId());
            } catch (org.samlier.store.StoreException unavailable) {
                return null;
            }
        });
        var m3Attested = ApprovedAttestedCaseRegistry.create(
                definitions, org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M3);
        var m3Config = ApprovedConfigCaseRegistry.create(
                definitions, org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M3);
        var m3Browser = ApprovedBrowserCaseRegistry.create(
                definitions, config.publicBaseUrl(),
                org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M3);
        var m3Automated = M3AutomatedCaseRegistry.create(
                runId -> {
                    var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
                    try { return metadataCache.get(run.planId()); }
                    catch (org.samlier.store.StoreException unavailable) { return null; }
                },
                transcriptContent,
                runId -> {
                    var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
                    var plan = plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
                    try { return targetCertificates.certificatesFor(plan); }
                    catch (RuntimeException unavailable) { return List.of(); }
                },
                runId -> {
                    var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
                    return java.util.Optional.of(keys.getOrCreate(run.planId()).privateKey());
                });
        var interactiveRegistry = org.samlier.runner.TestCaseRegistry.merge(
                m1Attested, m1Config, m1Browser,
                m2Automated, m2Attested, m2Config, m2Browser,
                m3Automated, m3Attested, m3Config, m3Browser);
        var executionService = new CaseExecutionService(caseExecutions);
        var caseContexts = (org.samlier.runner.CaseContextProvider) runId -> caseContext(
                runId, plans, runs, transcript, clock);
        var starters = Map.of(
                org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M1, List.of(
                        new ApprovedCaseStarter(coverage, definitions, m1Attested, executionService, applicability),
                        new ApprovedCaseStarter(coverage, definitions, m1Config, executionService, applicability),
                        new ApprovedCaseStarter(coverage, definitions, m1Browser, executionService, applicability)),
                org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M2, List.of(
                        new ApprovedCaseStarter(coverage, definitions, m2Automated, executionService, applicability),
                        new ApprovedCaseStarter(coverage, definitions, m2Attested, executionService, applicability),
                        new ApprovedCaseStarter(coverage, definitions, m2Config, executionService, applicability),
                        new ApprovedCaseStarter(coverage, definitions, m2Browser, executionService, applicability)),
                org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M3, List.of(
                        new ApprovedCaseStarter(coverage, definitions, m3Automated, executionService, applicability),
                        new ApprovedCaseStarter(coverage, definitions, m3Attested, executionService, applicability),
                        new ApprovedCaseStarter(coverage, definitions, m3Config, executionService, applicability),
                        new ApprovedCaseStarter(coverage, definitions, m3Browser, executionService, applicability)));
        var pendingInteractions = new PendingInteractionService(caseExecutions, interactiveRegistry);
        var bootstrapContracts = new BootstrapContractService(
                definitions, caseExecutions, plans, runs, transcript, metadataLab);
        var protocolEvidence = new ProtocolEvidenceAutomationService(
                caseExecutions, interactiveRegistry, executionService, caseContexts);
        var attestations = new AttestationService(interactiveRegistry, executionService, caseContexts);
        var configurations = new ConfigurationService(interactiveRegistry, executionService, caseContexts);
        var browserCompletions = new BrowserCompletionService(interactiveRegistry, executionService, caseContexts);
        var evaluator = new RunEvaluationService(
                coverage, plans, runs,
                new CaseRunProjection(caseExecutions, definitions.byId().keySet()), applicability,
                new OutboxIncidentProjection(caseExecutions));
        var artifacts = new FileRunArtifactRepository(config.dataDirectory());
        ResultPublicationService results = null;
        if (!config.suiteImageDigest().isBlank()) {
            var components = EvaluationArtifactDigests.fromDocuments(
                    documents.bytes("tests/coverage.yaml"), documents.testDefinitions(),
                    documents.bytes("tests/specs.yaml"));
            var contexts = new DefaultResultContextProvider(
                    new ResultDocumentContext.Suite(
                            "Samlier", "0.1.0", config.suiteImageDigest(),
                            config.mode().name().toLowerCase(Locale.ROOT)),
                    components,
                    URI.create("https://github.com/sgrastar/samlier/blob/main/docs/04-requirement-coverage.md"),
                    URI.create("https://github.com/sgrastar/samlier/blob/main/tests/cases.yaml"),
                    plan -> metadataCache.get(plan.id()));
            results = new ResultPublicationService(
                    coverage, evaluator, contexts, new ResultJsonWriter(), artifacts);
        }
        var access = new RunAccessService(
                config.publicBaseUrl(), runs, new SqliteRunAccessGrantRepository(database), clock);
        var publications = new SqlitePublicationRepository(database);
        return new M1Runtime(
                config, quickCheck, results, artifacts, access, plans, runs, transcript, clock,
                starters, pendingInteractions, bootstrapContracts, protocolEvidence, attestations,
                configurations, browserCompletions, caseExecutions, publications);
    }

    QuickCheckService.QuickCheckResult quickCheck(String runId) {
        var value = quickCheck.execute(runId);
        var run = requireRun(runId);
        var plan = requirePlan(run);
        startInteractive(run, plan, org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M1);
        if (results != null) results.generate(runId);
        return value;
    }

    java.util.List<org.samlier.runner.InteractionQuery.PendingInteraction> pending(String runId) {
        requireRun(runId);
        return pendingInteractions.pending(runId);
    }

    java.util.List<org.samlier.runner.BootstrapContractQuery.BootstrapContract> bootstrapContracts(String runId) {
        requireRun(runId);
        return bootstrapContracts.contracts(runId);
    }

    org.samlier.runner.ProtocolEvidenceAutomationService.Status protocolEvidence(String runId) {
        requireRun(runId);
        return protocolEvidence.status(runId);
    }

    org.samlier.runner.ProtocolEvidenceAutomationService.Evaluation evaluateProtocolEvidence(String runId) {
        requireRun(runId);
        var value = protocolEvidence.evaluateReady(runId);
        if (results != null) results.generate(runId);
        return value;
    }

    org.samlier.runner.AttestationExecutor.Result attest(
            String runId, String caseId, String value, String note) {
        var result = attestations.attest(runId, caseId, value, note);
        if (results != null) results.generate(runId);
        return result;
    }

    org.samlier.runner.ConfigurationExecutor.Result configure(
            String runId, String caseId, String value, String note) {
        var result = configurations.answer(runId, caseId, value, note);
        if (results != null) results.generate(runId);
        return result;
    }

    org.samlier.runner.BrowserCompletionExecutor.Result completeBrowser(String runId, String caseId) {
        var result = browserCompletions.complete(runId, caseId);
        if (results != null) results.generate(runId);
        return result;
    }

    java.util.List<org.samlier.core.caseexec.CaseExecution> startMilestone(
            String runId, String milestoneName) {
        var milestone = parseMilestone(milestoneName);
        var run = requireRun(runId);
        var plan = requirePlan(run);
        if (milestone == org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.M3
                && plan.profile() == org.samlier.core.plan.PlanProfile.IDP_FULL
                && !org.samlier.runner.outbox.EcpProbeService.allRequiredFixturesSent(caseExecutions, run.id())) {
            throw new IllegalArgumentException(
                    "Run the ECP, channel-binding, and SAML-EC probes before starting M3 for an IdP Full Profile Run");
        }
        var started = startInteractive(run, plan, milestone);
        if (results != null) results.generate(runId);
        return started;
    }

    byte[] requireResult(String runId) {
        return artifacts.findResult(runId)
                .orElseThrow(() -> new IllegalArgumentException(
                        results == null
                                ? "Result generation requires SAMLIER_IMAGE_DIGEST"
                                : "Result artifact has not been generated"));
    }

    byte[] requireReport(String runId) {
        if (results == null) throw new IllegalArgumentException("Report generation requires SAMLIER_IMAGE_DIGEST");
        return results.requireReport(runId);
    }

    PublicationRoutes.Published publish(String runId) {
        if (config.mode() != AppConfig.Mode.HOSTED || !config.publishEnabled()) {
            throw new IllegalArgumentException("Hosted publication is disabled; export report.html locally instead");
        }
        requireRun(runId);
        if (results == null) throw new IllegalArgumentException("Publication requires SAMLIER_IMAGE_DIGEST");
        results.generate(runId);
        publications.publish(runId, clock.instant());
        return new PublicationRoutes.Published(runId, config.publicBaseUrl().resolve("/reports/" + runId));
    }

    boolean isPublished(String runId) {
        return config.mode() == AppConfig.Mode.HOSTED && publications.isPublished(runId);
    }

    RunAccessService.ManagementSession exchange(String runId, String token) {
        if (config.mode() != AppConfig.Mode.HOSTED) {
            throw new IllegalArgumentException("Management sessions are available only in hosted mode");
        }
        return access.exchange(runId, token);
    }

    void authorize(String runId, String sessionToken) {
        if (config.mode() == AppConfig.Mode.HOSTED) access.authorize(runId, sessionToken);
    }

    void authorizeMutation(String runId, String sessionToken, String csrfToken) {
        if (config.mode() == AppConfig.Mode.HOSTED) access.authorizeMutation(runId, sessionToken, csrfToken);
    }

    java.util.List<org.samlier.core.plan.TestPlan> authorizedPlans(String sessionToken) {
        if (config.mode() != AppConfig.Mode.HOSTED) return plans.list();
        var run = requireRun(access.authorizeSession(sessionToken));
        return java.util.List.of(requirePlan(run));
    }

    void authorizePlan(String planId, String sessionToken) {
        if (config.mode() != AppConfig.Mode.HOSTED) return;
        var run = requireRun(access.authorizeSession(sessionToken));
        if (!run.planId().equals(planId)) throw new SecurityException("Access denied");
    }

    void authorizePlanMutation(String planId, String sessionToken, String csrfToken) {
        if (config.mode() != AppConfig.Mode.HOSTED) return;
        var runId = access.authorizeSession(sessionToken);
        var run = requireRun(runId);
        if (!run.planId().equals(planId)) throw new SecurityException("Access denied");
        access.authorizeMutation(runId, sessionToken, csrfToken);
    }

    org.samlier.runner.access.RunAccessService.PreparedAccess prepareManagementAccess(TestRun run) {
        if (config.mode() != AppConfig.Mode.HOSTED) {
            throw new IllegalStateException("Prepared management access is only used in Hosted mode");
        }
        return access.prepareIssue(run.id());
    }

    private TestRun requireRun(String runId) {
        return runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
    }

    private org.samlier.core.plan.TestPlan requirePlan(TestRun run) {
        return plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
    }

    private org.samlier.core.caseexec.CaseContext caseContext(String runId) {
        var run = requireRun(runId);
        return caseContext(run, requirePlan(run));
    }

    private java.util.List<org.samlier.core.caseexec.CaseExecution> startInteractive(
            TestRun run,
            org.samlier.core.plan.TestPlan plan,
            org.samlier.core.casedef.CaseDefinitionCatalog.Milestone milestone) {
        if (run.status() != org.samlier.core.run.RunStatus.COMPLETED) {
            throw new IllegalArgumentException("Milestone execution requires a completed baseline SSO round trip");
        }
        var context = caseContext(run, plan);
        var started = new java.util.ArrayList<org.samlier.core.caseexec.CaseExecution>();
        starters.getOrDefault(milestone, List.of()).forEach(starter ->
                started.addAll(starter.startApplicable(run, plan, context)));
        return List.copyOf(started);
    }

    private org.samlier.core.casedef.CaseDefinitionCatalog.Milestone parseMilestone(String value) {
        try {
            return org.samlier.core.casedef.CaseDefinitionCatalog.Milestone.valueOf(
                    value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Unknown implementation milestone: " + value, invalid);
        }
    }

    private org.samlier.core.caseexec.CaseContext caseContext(
            TestRun run, org.samlier.core.plan.TestPlan plan) {
        return new DefaultCaseContext(
                run.id(), plan.profile().role(), clock, plan.parameters(), plan.interaction(),
                run.targetToSuiteReachability(), transcript,
                run.status() == org.samlier.core.run.RunStatus.COMPLETED);
    }

    private static org.samlier.core.caseexec.CaseContext caseContext(
            String runId,
            PlanRepository plans,
            RunRepository runs,
            TranscriptRecorder transcript,
            Clock clock) {
        var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
        var plan = plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
        return new DefaultCaseContext(
                run.id(), plan.profile().role(), clock, plan.parameters(), plan.interaction(),
                run.targetToSuiteReachability(), transcript,
                run.status() == org.samlier.core.run.RunStatus.COMPLETED);
    }
}
