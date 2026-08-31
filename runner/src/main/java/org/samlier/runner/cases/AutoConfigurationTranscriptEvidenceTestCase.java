package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.saml.crypto.SamlXmlDecrypter;

/** Uses target-generated SAML first and preserves the approved CONFIG questionnaire as fallback. */
public final class AutoConfigurationTranscriptEvidenceTestCase
        implements TestCase, ConfigurationPrompt, AttestationPrompt, org.samlier.runner.EvidenceCampaignCase {
    private final TestCase fallback;
    private final TranscriptContentReader content;
    private final SamlDecryptionKeyProvider decryptionKeys;

    public AutoConfigurationTranscriptEvidenceTestCase(
            TestCase fallback,
            TranscriptContentReader content,
            SamlDecryptionKeyProvider decryptionKeys) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.content = Objects.requireNonNull(content, "content");
        this.decryptionKeys = Objects.requireNonNull(decryptionKeys, "decryptionKeys");
        if (!TranscriptConfigurationObservation.supports(fallback.id())) {
            throw new IllegalArgumentException("No Transcript CONFIG oracle for " + fallback.id());
        }
        if (!(fallback instanceof ConfigurationPrompt) || !(fallback instanceof AttestationPrompt)) {
            throw new IllegalArgumentException("CONFIG fallback must expose both approved prompts");
        }
    }

    @Override public String id() { return fallback.id(); }
    @Override public TargetRole role() { return fallback.role(); }
    @Override public String evidenceCampaignId() { return "ordinary-sso-transcript"; }
    @Override public String evidenceCampaignTitle() { return "Ordinary browser SSO transcript"; }
    @Override public org.samlier.runner.RunCampaignQuery.ActionKind evidenceActionKind() {
        return org.samlier.runner.RunCampaignQuery.ActionKind.LOGIN;
    }
    @Override public String instructionEn() { return ((ConfigurationPrompt) fallback).instructionEn(); }
    @Override public String promptEn() { return ((AttestationPrompt) fallback).promptEn(); }
    @Override public java.util.List<AttestationOption> options() {
        return ((AttestationPrompt) fallback).options();
    }

    @Override
    public CaseStep start(CaseContext context) {
        var messages = new ArrayList<TranscriptConfigurationObservation.Message>();
        for (var entry : context.transcript().list(context.runId())) {
            if (entry.direction() != Direction.INBOUND
                    || entry.decodedSamlRef() == null || entry.decodedSamlBytes() <= 0
                    || !"Response".equals(entry.samlSummary().get("type"))) continue;
            messages.add(new TranscriptConfigurationObservation.Message(
                    "transcript:" + entry.id(), content.readDecodedSaml(entry)));
        }
        var key = decryptionKeys.keyFor(context.runId()).orElse(null);
        var outcome = TranscriptConfigurationObservation.evaluate(
                id(), messages, key, new SamlXmlDecrypter());
        return outcome.<CaseStep>map(CaseStep.Finish::new).orElseGet(() -> fallback.start(context));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        return fallback.resume(context, state, event);
    }
}
