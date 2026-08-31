package org.samlier.runner.cases;

import java.util.Objects;
import java.util.function.Function;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.runner.EvidenceCampaignCase;
import org.samlier.runner.FallbackEvidenceCase;
import org.samlier.runner.RunCampaignQuery;

/** Uses conclusive target metadata first and preserves the approved attestation as fallback. */
public final class AutoAttestedMetadataEvidenceTestCase
        implements TestCase, AttestationPrompt, EvidenceCampaignCase, FallbackEvidenceCase {
    private final TestCase fallback;
    private final Function<String, byte[]> targetMetadata;

    public AutoAttestedMetadataEvidenceTestCase(
            TestCase fallback, Function<String, byte[]> targetMetadata) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.targetMetadata = Objects.requireNonNull(targetMetadata, "targetMetadata");
        if (!(fallback instanceof AttestationPrompt) || !TargetMetadataObservation.supports(fallback.id())) {
            throw new IllegalArgumentException("No approved target-metadata fallback for " + fallback.id());
        }
    }

    @Override public String id() { return fallback.id(); }
    @Override public TargetRole role() { return fallback.role(); }
    @Override public String promptEn() { return ((AttestationPrompt) fallback).promptEn(); }
    @Override public java.util.List<AttestationOption> options() {
        return ((AttestationPrompt) fallback).options();
    }
    @Override public String evidenceCampaignId() { return "target-metadata-inspection"; }
    @Override public String evidenceCampaignTitle() { return "Passive target metadata inspection"; }
    @Override public RunCampaignQuery.ActionKind evidenceActionKind() {
        return RunCampaignQuery.ActionKind.NONE;
    }

    @Override
    public boolean resolvedFromExternalEvidence(CaseExecution execution) {
        return execution.outcome() != null && execution.outcome().evidence().stream()
                .anyMatch(value -> "target-metadata".equals(value.kind()));
    }

    @Override
    public CaseStep start(CaseContext context) {
        byte[] metadata;
        try { metadata = targetMetadata.apply(context.runId()); }
        catch (RuntimeException unavailable) { metadata = null; }
        var outcome = TargetMetadataObservation.evaluate(id(), metadata, context.clock().instant());
        return outcome.<CaseStep>map(CaseStep.Finish::new).orElseGet(() -> fallback.start(context));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        return fallback.resume(context, state, event);
    }
}
