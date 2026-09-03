package com.samlscope.runner.cases;

import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.core.transcript.TranscriptContentReader;

public final class SamlAssertionSchemaTranscriptTestCase implements TestCase {
    public static final String CASE_ID = "IIP-SSO01-dw-idp-01";
    private final TranscriptContentReader content;
    private final SamlDecryptionKeyProvider keys;

    public SamlAssertionSchemaTranscriptTestCase(TranscriptContentReader content, SamlDecryptionKeyProvider keys) {
        this.content = Objects.requireNonNull(content, "content");
        this.keys = Objects.requireNonNull(keys, "keys");
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive assertion cases require a complete Transcript");
        return new CaseStep.Finish(new SamlAssertionSchemaCase().evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content),
                keys.keyFor(context.runId()).orElse(null)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive assertion cases finish during start");
    }
}
