package org.samlier.runner.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.samlier.saml.crypto.SamlElementDecrypter;
import org.samlier.saml.crypto.SamlXmlDecrypter;
import org.samlier.saml.normal.SamlException;
import org.samlier.saml.normal.SecureXml;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptContentReader;

/** Finishes from an ordinary SSO Transcript when conclusive; otherwise waits for more evidence. */
public final class AutoBrowserEvidenceTestCase
        implements TestCase, BrowserPrompt, ProtocolEvidenceCase {
    private final BrowserEvidenceTestCase fallback;
    private final TranscriptContentReader content;
    private final SamlDecryptionKeyProvider decryptionKeys;
    private final Function<String, Optional<String>> targetEntityIds;
    private final SamlElementDecrypter decrypter;

    public AutoBrowserEvidenceTestCase(BrowserEvidenceTestCase fallback, TranscriptContentReader content) {
        this(fallback, content, ignored -> Optional.empty(), ignored -> Optional.empty(), new SamlXmlDecrypter());
    }

    public AutoBrowserEvidenceTestCase(
            BrowserEvidenceTestCase fallback,
            TranscriptContentReader content,
            SamlDecryptionKeyProvider decryptionKeys) {
        this(fallback, content, decryptionKeys, ignored -> Optional.empty(), new SamlXmlDecrypter());
    }

    public AutoBrowserEvidenceTestCase(
            BrowserEvidenceTestCase fallback,
            TranscriptContentReader content,
            SamlDecryptionKeyProvider decryptionKeys,
            Function<String, Optional<String>> targetEntityIds) {
        this(fallback, content, decryptionKeys, targetEntityIds, new SamlXmlDecrypter());
    }

    AutoBrowserEvidenceTestCase(
            BrowserEvidenceTestCase fallback,
            TranscriptContentReader content,
            SamlDecryptionKeyProvider decryptionKeys,
            Function<String, Optional<String>> targetEntityIds,
            SamlElementDecrypter decrypter) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.content = Objects.requireNonNull(content, "content");
        this.decryptionKeys = Objects.requireNonNull(decryptionKeys, "decryptionKeys");
        this.targetEntityIds = Objects.requireNonNull(targetEntityIds, "targetEntityIds");
        this.decrypter = Objects.requireNonNull(decrypter, "decrypter");
        if (!NormalFlowBrowserObservation.supports(fallback.id())) {
            throw new IllegalArgumentException("No normal-flow oracle for " + fallback.id());
        }
    }

    @Override public String id() { return fallback.id(); }
    @Override public TargetRole role() { return fallback.role(); }
    @Override public String browserInstructionsEn() { return fallback.browserInstructionsEn(); }

    @Override
    public CaseStep start(CaseContext context) {
        Objects.requireNonNull(context, "context");
        var outcome = transcriptOutcome(context);
        if (outcome.isPresent()) return new CaseStep.Finish(outcome.orElseThrow());
        return fallback.start(context);
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        if (event instanceof CaseEvent.TranscriptReady) {
            return transcriptOutcome(context)
                    .<CaseStep>map(CaseStep.Finish::new)
                    .orElseThrow(() -> new IllegalStateException("Transcript evidence is not ready"));
        }
        return fallback.resume(context, state, event);
    }

    @Override
    public EvidenceStatus evidenceStatus(CaseContext context) {
        var outcome = transcriptOutcome(context);
        var required = List.of("conclusive-transcript:" + id());
        return new EvidenceStatus(
                outcome.isPresent(), required, outcome.isPresent() ? required : List.of(),
                outcome.<java.util.Map<String, Object>>map(value -> java.util.Map.of(
                        "outcome", value.outcome().name(),
                        "evidence_count", value.evidence().size())).orElseGet(java.util.Map::of));
    }

    private java.util.Optional<org.samlier.core.evaluation.CaseOutcome> transcriptOutcome(CaseContext context) {
        var messages = new ArrayList<NormalFlowBrowserObservation.Message>();
        var key = decryptionKeys.keyFor(context.runId()).orElse(null);
        for (var entry : context.transcript().list(context.runId())) {
            if (entry.decodedSamlRef() == null || entry.decodedSamlBytes() <= 0) continue;
            // Inbound SAML is recorded before protocol parsing and then updated atomically with
            // the validated summary. Never conclude from the durable-but-unvalidated first form.
            var parsedType = entry.samlSummary().get("type");
            if (entry.direction() == Direction.INBOUND
                    && (!"Response".equals(parsedType)
                    || !Boolean.TRUE.equals(entry.samlSummary().get("normalFlowAccepted")))) continue;
            if (entry.direction() == Direction.OUTBOUND && !"AuthnRequest".equals(parsedType)) continue;
            if (entry.correlationId() != null && entry.correlationId().startsWith("action_")) continue;
            if (entry.url() != null && entry.url().contains("mdv=")) continue;
            var xml = content.readDecodedSaml(entry);
            messages.add(new NormalFlowBrowserObservation.Message(
                    "transcript:" + entry.id(), entry.method(), entry.url(),
                    key == null ? xml : decryptAssertions(xml, key)));
        }
        return NormalFlowBrowserObservation.evaluate(
                id(), messages, targetEntityIds.apply(context.runId()).orElse(null));
    }

    /** Produces an in-memory observation view; plaintext is never submitted to TranscriptRecorder. */
    private byte[] decryptAssertions(byte[] xml, java.security.PrivateKey key) {
        try {
            var document = SecureXml.parse(xml);
            var nodes = document.getElementsByTagNameNS(
                    NormalFlowBrowserObservation.ASSERTION, "EncryptedAssertion");
            var wrappers = new ArrayList<org.w3c.dom.Element>();
            for (var index = 0; index < nodes.getLength(); index++) {
                wrappers.add((org.w3c.dom.Element) nodes.item(index));
            }
            for (var wrapper : wrappers) {
                var plaintext = decrypter.decrypt(wrapper, key);
                wrapper.getParentNode().replaceChild(document.importNode(plaintext, true), wrapper);
            }
            return wrappers.isEmpty() ? xml : SecureXml.serialize(document);
        } catch (SamlException unavailable) {
            // Keep the encrypted original. The oracle remains inconclusive until evidence arrives or the wait expires.
            return xml;
        }
    }
}
