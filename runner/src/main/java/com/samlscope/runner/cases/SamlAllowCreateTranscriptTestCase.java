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

public final class SamlAllowCreateTranscriptTestCase implements TestCase {
    private static final Map<String, SamlAllowCreateCase.Rule> RULES = Map.of(
            "IIP-SSO01-fl-sp-01", SamlAllowCreateCase.Rule.GENERAL_INTEROPERABILITY,
            "IIP-SSO01-fn-sp-01", SamlAllowCreateCase.Rule.TRANSIENT_ABSENT);
    private final String id;
    private final TranscriptContentReader content;

    public SamlAllowCreateTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved AllowCreate case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.SP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive AllowCreate cases require a complete Transcript");
        return new CaseStep.Finish(new SamlAllowCreateCase(RULES.get(id)).evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive AllowCreate cases finish during start");
    }
}
