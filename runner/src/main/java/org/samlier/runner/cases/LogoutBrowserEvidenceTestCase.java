package org.samlier.runner.cases;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.TranscriptContentReader;

/** Completes approved SLO browser cases from target-emitted protocol evidence after a user logout action. */
public final class LogoutBrowserEvidenceTestCase
        implements TestCase, BrowserPrompt, ProtocolEvidenceCase, org.samlier.runner.EvidenceCampaignCase {
    private static final Map<String, LogoutTranscriptProfileCase.Rule> RULES = Map.ofEntries(
            Map.entry("IIP-IDP17-f-idp-01", LogoutTranscriptProfileCase.Rule.RESPONSE_ISSUER_COUNT),
            Map.entry("IIP-IDP17-g-idp-01", LogoutTranscriptProfileCase.Rule.RESPONSE_ISSUER_VALUE),
            Map.entry("IIP-IDP17-h-idp-01", LogoutTranscriptProfileCase.Rule.RESPONSE_ISSUER_FORMAT),
            Map.entry("IIP-IDP17-i-idp-01", LogoutTranscriptProfileCase.Rule.RESPONSE_SIGNATURE),
            Map.entry("IIP-IDP17-j-idp-01", LogoutTranscriptProfileCase.Rule.REQUEST_ISSUER_COUNT),
            Map.entry("IIP-IDP17-k-idp-01", LogoutTranscriptProfileCase.Rule.REQUEST_ISSUER_VALUE),
            Map.entry("IIP-IDP17-l-idp-01", LogoutTranscriptProfileCase.Rule.REQUEST_ISSUER_FORMAT),
            Map.entry("IIP-IDP17-m-idp-01", LogoutTranscriptProfileCase.Rule.REQUEST_SIGNATURE),
            Map.entry("IIP-IDP17-n-idp-01", LogoutTranscriptProfileCase.Rule.REQUEST_IDENTIFIER_MATCH),
            Map.entry("IIP-IDP17-t-idp-01", LogoutTranscriptProfileCase.Rule.REQUEST_NOT_ON_OR_AFTER),
            Map.entry("IIP-IDP17-u-idp-01", LogoutTranscriptProfileCase.Rule.REQUEST_NOT_ON_OR_AFTER_BOUND),
            Map.entry("IIP-IDP18-a-idp-01", LogoutTranscriptProfileCase.Rule.REDIRECT_LOGOUT_REQUEST_ACCEPTED));

    private final BrowserEvidenceTestCase fallback;
    private final TranscriptContentReader content;
    private final Function<String, Optional<String>> targetEntityIds;
    private final Function<String, List<X509Certificate>> signingCertificates;

    public LogoutBrowserEvidenceTestCase(
            BrowserEvidenceTestCase fallback,
            TranscriptContentReader content,
            Function<String, Optional<String>> targetEntityIds,
            Function<String, List<X509Certificate>> signingCertificates) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.content = Objects.requireNonNull(content, "content");
        this.targetEntityIds = Objects.requireNonNull(targetEntityIds, "targetEntityIds");
        this.signingCertificates = Objects.requireNonNull(signingCertificates, "signingCertificates");
        if (!supports(fallback.id())) throw new IllegalArgumentException("No SLO oracle for " + fallback.id());
    }

    static boolean supports(String caseId) { return RULES.containsKey(caseId); }
    @Override public String id() { return fallback.id(); }
    @Override public TargetRole role() { return fallback.role(); }
    @Override public String evidenceCampaignId() { return "target-initiated-logout"; }
    @Override public String evidenceCampaignTitle() { return "Target-initiated browser logout"; }
    @Override public org.samlier.runner.RunCampaignQuery.ActionKind evidenceActionKind() {
        return org.samlier.runner.RunCampaignQuery.ActionKind.LOGIN;
    }
    @Override public String browserInstructionsEn() { return fallback.browserInstructionsEn(); }

    @Override public CaseStep start(CaseContext context) {
        var result = observed(context);
        return result.<CaseStep>map(CaseStep.Finish::new).orElseGet(() -> fallback.start(context));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        if (event instanceof CaseEvent.TranscriptReady) {
            return observed(context).<CaseStep>map(CaseStep.Finish::new)
                    .orElseThrow(() -> new IllegalStateException("SLO Transcript evidence is not ready"));
        }
        return fallback.resume(context, state, event);
    }

    @Override public EvidenceStatus evidenceStatus(CaseContext context) {
        var result = observed(context);
        var required = List.of("target-emitted-slo:" + id());
        return new EvidenceStatus(result.isPresent(), required, result.isPresent() ? required : List.of(),
                result.<Map<String, Object>>map(value -> Map.of(
                        "outcome", value.outcome().name(), "evidence_count", value.evidence().size()))
                        .orElseGet(Map::of));
    }

    private Optional<org.samlier.core.evaluation.CaseOutcome> observed(CaseContext context) {
        var outcome = new LogoutTranscriptProfileCase(
                RULES.get(id()), signingCertificates.apply(context.runId()),
                targetEntityIds.apply(context.runId()).orElse(null))
                .evaluate(context.runId(), context.transcript(), content);
        return outcome.evidence().isEmpty() ? Optional.empty() : Optional.of(outcome);
    }
}
