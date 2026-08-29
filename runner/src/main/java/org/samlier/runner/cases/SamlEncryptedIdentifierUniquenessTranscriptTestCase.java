package org.samlier.runner.cases;

import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.TranscriptContentReader;

public final class SamlEncryptedIdentifierUniquenessTranscriptTestCase implements TestCase {
    public static final String CASE_ID = "IIP-SSO01-dp-idp-01";
    private final TranscriptContentReader content;

    public SamlEncryptedIdentifierUniquenessTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!CASE_ID.equals(id)) throw new IllegalArgumentException("Unapproved encrypted identifier case id: " + id);
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Ciphertext uniqueness requires a complete Transcript");
        return new CaseStep.Finish(new SamlEncryptedIdentifierUniquenessCase().evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Ciphertext uniqueness finishes during start");
    }
}
