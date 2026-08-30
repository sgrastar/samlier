package org.samlier.runner.cases;

import java.security.cert.X509Certificate;
import java.net.URI;
import java.time.Duration;
import java.util.List;
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

/** Replaces an attestation with a passive SLO Transcript rule when the approved evidence permits it. */
public final class LogoutAttestedEvidenceTestCase
        implements TestCase, BrowserPrompt, ProtocolEvidenceCase, org.samlier.runner.EvidenceCampaignCase {
    private static final String WAITING = "await-slo-observation";
    private final String id;
    private final TargetRole role;
    private final URI publicBase;
    private final TranscriptContentReader content;
    private final Function<String, Optional<String>> targetEntityIds;
    private final Function<String, List<X509Certificate>> targetSigningCertificates;
    private final LogoutTranscriptProfileCase.Rule rule;

    public LogoutAttestedEvidenceTestCase(
            String id,
            TargetRole role,
            URI publicBase,
            TranscriptContentReader content,
            Function<String, Optional<String>> targetEntityIds,
            Function<String, List<X509Certificate>> targetSigningCertificates,
            LogoutTranscriptProfileCase.Rule rule) {
        this.id = Objects.requireNonNull(id, "id");
        this.role = Objects.requireNonNull(role, "role");
        this.publicBase = Objects.requireNonNull(publicBase, "publicBase");
        this.content = Objects.requireNonNull(content, "content");
        this.targetEntityIds = Objects.requireNonNull(targetEntityIds, "targetEntityIds");
        this.targetSigningCertificates = Objects.requireNonNull(
                targetSigningCertificates, "targetSigningCertificates");
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return role; }
    @Override public String evidenceCampaignId() { return "target-initiated-logout"; }
    @Override public String evidenceCampaignTitle() { return "Target-initiated browser logout"; }
    @Override public org.samlier.runner.RunCampaignQuery.ActionKind evidenceActionKind() {
        return org.samlier.runner.RunCampaignQuery.ActionKind.LOGIN;
    }
    @Override public String browserInstructionsEn() {
        return "Initiate logout from the target in this browser, then mark the step complete. "
                + "Samlier judges any target-issued LogoutRequest from the Transcript; do not enter a verdict.";
    }

    @Override
    public CaseStep start(CaseContext context) {
        return new CaseStep.AwaitBrowser(
                new CaseState(WAITING, java.util.Map.of("case_id", id)), List.of(),
                publicBase.resolve("/browser/" + context.runId() + "/" + id), Duration.ofDays(7));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        if (!WAITING.equals(state.phase()) || !id.equals(state.data().get("case_id"))) {
            throw new IllegalArgumentException("SLO observation state does not belong to this case");
        }
        if (event instanceof CaseEvent.TranscriptReady || event instanceof CaseEvent.BrowserReturned) {
            return new CaseStep.Finish(evaluate(context));
        }
        if (event instanceof CaseEvent.TimedOut) {
            return new CaseStep.Finish(org.samlier.core.evaluation.CaseOutcome.notVerified(
                    "timeout", "slo.observation.timeout"));
        }
        if (event instanceof CaseEvent.Aborted) {
            return new CaseStep.Finish(org.samlier.core.evaluation.CaseOutcome.notVerified(
                    "user_skipped", "slo.observation.aborted"));
        }
        throw new IllegalArgumentException("Expected browser or Transcript completion");
    }

    @Override
    public EvidenceStatus evidenceStatus(CaseContext context) {
        var outcome = evaluate(context);
        var ready = !outcome.evidence().isEmpty();
        var required = List.of("target-issued-logout-request");
        return new EvidenceStatus(
                ready, required, ready ? required : List.of(),
                java.util.Map.of("outcome", outcome.outcome().name(),
                        "evidence_count", outcome.evidence().size()));
    }

    private org.samlier.core.evaluation.CaseOutcome evaluate(CaseContext context) {
        var evaluator = new LogoutTranscriptProfileCase(
                rule, targetSigningCertificates.apply(context.runId()),
                targetEntityIds.apply(context.runId()).orElse(null));
        return evaluator.evaluate(context.runId(), context.transcript(), content);
    }
}
