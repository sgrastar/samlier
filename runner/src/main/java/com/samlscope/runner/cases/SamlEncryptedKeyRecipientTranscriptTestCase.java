package com.samlscope.runner.cases;

import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.core.transcript.TranscriptContentReader;

public final class SamlEncryptedKeyRecipientTranscriptTestCase implements TestCase {
    public static final String CASE_ID = "IIP-SSO01-dq-idp-01";
    private final TranscriptContentReader content;
    private final SamlEncryptedKeyRecipientCase oracle;

    public SamlEncryptedKeyRecipientTranscriptTestCase(
            String id, TranscriptContentReader content, String peerEntityId) {
        if (!CASE_ID.equals(id)) throw new IllegalArgumentException("Unapproved encrypted key case id: " + id);
        this.content = Objects.requireNonNull(content, "content");
        this.oracle = new SamlEncryptedKeyRecipientCase(peerEntityId);
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Encrypted key Recipient requires a complete Transcript");
        return new CaseStep.Finish(oracle.evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Encrypted key Recipient finishes during start");
    }
}
