package org.samlier.runner;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.xml.namespace.QName;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.run.RunRepository;
import org.samlier.core.run.RunStatus;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.cases.AutomatedCaseDependencies;
import org.samlier.runner.cases.AutomatedCaseRegistry;
import org.samlier.runner.cases.IdpErrorProbeConfiguration;
import org.samlier.runner.cases.PrincipalIdentityResolver;
import org.samlier.runner.cases.SamlAttributeReleaseFixture;
import org.samlier.runner.cases.SamlOptionalFieldObservationCase;
import org.samlier.runner.cases.TargetSigningCertificateProvider;
import org.samlier.saml.crypto.FilePlanKeyStore;

/** Executes the approved M1 automated subset as an operational check over a completed Transcript. */
public final class QuickCheckService implements QuickCheckExecutor {
    public static final String DISCLAIMER = "This is an operational check, not a conformance determination.";
    private final PlanRepository plans;
    private final RunRepository runs;
    private final TranscriptRecorder transcript;
    private final TranscriptContentReader transcriptContent;
    private final CaseExecutionRepository caseExecutions;
    private final FilePlanKeyStore keys;
    private final TargetSigningCertificateProvider targetSigningCertificates;
    private final URI peerBase;
    private final Clock clock;

    public QuickCheckService(
            PlanRepository plans,
            RunRepository runs,
            TranscriptRecorder transcript,
            TranscriptContentReader transcriptContent,
            CaseExecutionRepository caseExecutions,
            FilePlanKeyStore keys,
            TargetSigningCertificateProvider targetSigningCertificates,
            URI peerBase,
            Clock clock) {
        this.plans = Objects.requireNonNull(plans, "plans");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.transcriptContent = Objects.requireNonNull(transcriptContent, "transcriptContent");
        this.caseExecutions = Objects.requireNonNull(caseExecutions, "caseExecutions");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.targetSigningCertificates = Objects.requireNonNull(
                targetSigningCertificates, "targetSigningCertificates");
        this.peerBase = Objects.requireNonNull(peerBase, "peerBase");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public QuickCheckResult execute(String runId) {
        var run = runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
        if (run.status() != RunStatus.COMPLETED) {
            throw new IllegalArgumentException("Quick check requires a completed SSO round trip");
        }
        var plan = plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
        var credentials = keys.getOrCreate(plan.id());
        var registry = AutomatedCaseRegistry.create(new AutomatedCaseDependencies(
                transcriptContent,
                attributeFixtures(),
                optionalSelectors(),
                targetSigningCertificates.certificatesFor(plan),
                peerBase.resolve("/p/" + plan.id()).toString(),
                ignored -> Optional.of(credentials.privateKey()),
                (ignored, identifier) -> PrincipalIdentityResolver.Resolution.unknown(),
                caseExecutions,
                inactiveProbe(plan.id())));
        var context = new DefaultCaseContext(
                run.id(), plan.profile().role(), clock, plan.parameters(),
                run.targetToSuiteReachability(), transcript, true);
        var snapshot = new AutomatedCaseRunner(registry, new CaseExecutionService(caseExecutions))
                .startReady(run.id(), context);
        return new QuickCheckResult(run.id(), DISCLAIMER, snapshot);
    }

    private IdpErrorProbeConfiguration inactiveProbe(String planId) {
        var issuer = peerBase.resolve("/p/" + planId);
        return new IdpErrorProbeConfiguration(
                peerBase.resolve("/p/" + planId + "/inactive-idp-probe"),
                issuer.toString(),
                peerBase.resolve("/p/" + planId + "/sp/acs/0"),
                Duration.ofMinutes(2), false, false, false);
    }

    private Map<String, SamlAttributeReleaseFixture> attributeFixtures() {
        return Map.of(
                "IIP-SSO01-dj-idp-01", new SamlAttributeReleaseFixture("no-values", null, List.of()),
                "IIP-SSO01-dk-idp-01", new SamlAttributeReleaseFixture(
                        "empty", null, List.of(SamlAttributeReleaseFixture.EmptyValue.INSTANCE)),
                "IIP-SSO01-dl-idp-01", new SamlAttributeReleaseFixture(
                        "null", null, List.of(SamlAttributeReleaseFixture.NullValue.INSTANCE)),
                "IIP-SSO01-du-idp-01", new SamlAttributeReleaseFixture(
                        "discrete", null, List.of(
                                new SamlAttributeReleaseFixture.TextValue("one"),
                                new SamlAttributeReleaseFixture.TextValue("two"))));
    }

    private Map<String, SamlOptionalFieldObservationCase.Selector> optionalSelectors() {
        var selector = SamlOptionalFieldObservationCase.Selector.element(new QName(
                "urn:oasis:names:tc:SAML:2.0:protocol", "Extensions"));
        return Map.of("IIP-SSO07-a-idp-01", selector, "IIP-SSO07-a-sp-01", selector);
    }

    public record QuickCheckResult(String runId, String disclaimer, List<CaseExecution> cases) {
        public QuickCheckResult { cases = List.copyOf(cases); }
    }
}
