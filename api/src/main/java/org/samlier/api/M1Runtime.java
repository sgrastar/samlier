package org.samlier.api;

import java.net.URI;
import java.time.Clock;
import java.util.Locale;
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
import org.samlier.runner.CatalogApplicabilityProvider;
import org.samlier.runner.CaseExecutionService;
import org.samlier.runner.DefaultCaseContext;
import org.samlier.runner.OutboxIncidentProjection;
import org.samlier.runner.PendingInteractionService;
import org.samlier.runner.PersistedApplicabilityInputProvider;
import org.samlier.runner.QuickCheckService;
import org.samlier.runner.RunEvaluationService;
import org.samlier.runner.access.RunAccessService;
import org.samlier.runner.cases.CachedTargetSigningCertificateProvider;
import org.samlier.runner.cases.ApprovedAttestedCaseRegistry;
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

/** M1 composition kept outside the G2-protected HTTP root so its boundaries remain independently testable. */
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
    private final ApprovedCaseStarter attestedStarter;
    private final PendingInteractionService pendingInteractions;
    private final AttestationService attestations;

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
            ApprovedCaseStarter attestedStarter,
            PendingInteractionService pendingInteractions,
            AttestationService attestations) {
        this.config = config;
        this.quickCheck = quickCheck;
        this.results = results;
        this.artifacts = artifacts;
        this.access = access;
        this.plans = plans;
        this.runs = runs;
        this.transcript = transcript;
        this.clock = clock;
        this.attestedStarter = attestedStarter;
        this.pendingInteractions = pendingInteractions;
        this.attestations = attestations;
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
            Clock clock) {
        var documents = CatalogDocuments.load();
        var coverage = CoverageCatalogMapper.fromDocument(documents.parsed("tests/coverage.yaml"));
        var predicates = PredicateCatalogMapper.fromDocument(documents.parsed("tests/predicates.yaml"));
        var definitions = CaseDefinitionCatalogMapper.fromDocument(documents.parsed("tests/cases.yaml"));
        var caseExecutions = new SqliteCaseExecutionRepository(database, json);
        var targetCertificates = new CachedTargetSigningCertificateProvider(metadataCache, metadataParser);
        var quickCheck = new QuickCheckService(
                plans, runs, transcript, transcriptContent, caseExecutions, keys, targetCertificates,
                config.peerBaseUrl(), clock, definitions);
        var applicability = new CatalogApplicabilityProvider(
                coverage, predicates,
                new PersistedApplicabilityInputProvider(new SqliteApplicabilityInputRepository(database, json)));
        var attestedRegistry = ApprovedAttestedCaseRegistry.create(definitions);
        var executionService = new CaseExecutionService(caseExecutions);
        var caseContexts = (org.samlier.runner.CaseContextProvider) runId -> caseContext(
                runId, plans, runs, transcript, clock);
        var attestedStarter = new ApprovedCaseStarter(
                coverage, definitions, attestedRegistry, executionService, applicability);
        var pendingInteractions = new PendingInteractionService(caseExecutions, attestedRegistry);
        var attestations = new AttestationService(attestedRegistry, executionService, caseContexts);
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
        return new M1Runtime(
                config, quickCheck, results, artifacts, access, plans, runs, transcript, clock,
                attestedStarter, pendingInteractions, attestations);
    }

    QuickCheckService.QuickCheckResult quickCheck(String runId) {
        var value = quickCheck.execute(runId);
        var run = requireRun(runId);
        var plan = requirePlan(run);
        attestedStarter.startApplicable(run, plan, caseContext(run, plan));
        if (results != null) results.generate(runId);
        return value;
    }

    java.util.List<org.samlier.runner.InteractionQuery.PendingInteraction> pending(String runId) {
        requireRun(runId);
        return pendingInteractions.pending(runId);
    }

    org.samlier.runner.AttestationExecutor.Result attest(
            String runId, String caseId, String value, String note) {
        var result = attestations.attest(runId, caseId, value, note);
        if (results != null) results.generate(runId);
        return result;
    }

    byte[] requireResult(String runId) {
        return artifacts.findResult(runId)
                .orElseThrow(() -> new IllegalArgumentException(
                        results == null
                                ? "Result generation requires SAMLIER_IMAGE_DIGEST"
                                : "Result artifact has not been generated"));
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

    String issueManagementUrl(TestRun run) {
        return config.mode() == AppConfig.Mode.HOSTED ? access.issue(run.id()).managementUrl().toString() : null;
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
