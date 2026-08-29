package org.samlier.runner.cases;

import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.TranscriptContentReader;

public final class SamlDecryptedTypeTranscriptTestCase implements TestCase {
    public static final String CASE_ID = "IIP-SSO01-do-idp-01";
    private final TranscriptContentReader content;
    private final SamlDecryptionKeyProvider keys;

    public SamlDecryptedTypeTranscriptTestCase(TranscriptContentReader content, SamlDecryptionKeyProvider keys) {
        this.content = Objects.requireNonNull(content, "content");
        this.keys = Objects.requireNonNull(keys, "keys");
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive decryption cases require a complete Transcript");
        return new CaseStep.Finish(new SamlDecryptedTypeCase().evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content),
                keys.keyFor(context.runId()).orElse(null)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive decryption cases finish during start");
    }
}
