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

public final class SamlIdentifierDeclarationTranscriptTestCase implements TestCase {
    private static final Map<String, TargetRole> DEFINITIONS = Map.of(
            "IIP-SSO01-cc-idp-01", TargetRole.IDP,
            "IIP-SSO01-cc-sp-01", TargetRole.SP);
    private final String id;
    private final TranscriptContentReader content;

    public SamlIdentifierDeclarationTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!DEFINITIONS.containsKey(id)) throw new IllegalArgumentException("Unapproved identifier declaration case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return DEFINITIONS.get(id); }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive identifier cases require a complete Transcript");
        return new CaseStep.Finish(new SamlIdentifierDeclarationCase().evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive identifier cases finish during start");
    }
}
