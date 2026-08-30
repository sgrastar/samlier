package org.samlier.runner.cases;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;

/** Uses target metadata first and falls back to the approved CONFIG interaction when inconclusive. */
public final class AutoConfigurationEvidenceTestCase
        implements TestCase, ConfigurationPrompt, AttestationPrompt {
    private final TestCase fallback;
    private final Function<String, byte[]> targetMetadata;

    public AutoConfigurationEvidenceTestCase(TestCase fallback, Function<String, byte[]> targetMetadata) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.targetMetadata = Objects.requireNonNull(targetMetadata, "targetMetadata");
        if (!TargetMetadataObservation.supports(fallback.id())) {
            throw new IllegalArgumentException("No target-metadata oracle for " + fallback.id());
        }
        if (!(fallback instanceof ConfigurationPrompt) || !(fallback instanceof AttestationPrompt)) {
            throw new IllegalArgumentException("CONFIG fallback must expose both approved prompts");
        }
    }

    @Override public String id() { return fallback.id(); }
    @Override public TargetRole role() { return fallback.role(); }
    @Override public String instructionEn() { return ((ConfigurationPrompt) fallback).instructionEn(); }
    @Override public String promptEn() { return ((AttestationPrompt) fallback).promptEn(); }
    @Override public java.util.List<AttestationOption> options() {
        return ((AttestationPrompt) fallback).options();
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
