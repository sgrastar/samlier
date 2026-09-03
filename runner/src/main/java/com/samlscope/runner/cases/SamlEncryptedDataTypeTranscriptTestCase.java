package com.samlscope.runner.cases;

import java.util.Map;
import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.core.transcript.TranscriptContentReader;

public final class SamlEncryptedDataTypeTranscriptTestCase implements TestCase {
    private static final Map<String, SamlEncryptedDataTypeCase.Rule> RULES = Map.of(
            "IIP-SSO01-dm-idp-01", SamlEncryptedDataTypeCase.Rule.TYPE_PRESENT,
            "IIP-SSO01-dn-idp-01", SamlEncryptedDataTypeCase.Rule.TYPE_IS_ELEMENT);
    private final String id;
    private final TranscriptContentReader content;

    public SamlEncryptedDataTypeTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved encrypted-data Type case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive encrypted-data Type cases require a complete Transcript");
        return new CaseStep.Finish(new SamlEncryptedDataTypeCase(RULES.get(id)).evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive encrypted-data Type cases finish during start");
    }
}
