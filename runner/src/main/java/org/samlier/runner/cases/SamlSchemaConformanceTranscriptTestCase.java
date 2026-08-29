package org.samlier.runner.cases;

import java.util.Map;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.TranscriptContentReader;

public final class SamlSchemaConformanceTranscriptTestCase implements TestCase {
    private record Definition(SamlSchemaConformanceCase.Rule rule, TargetRole role) {}
    private static final Map<String, Definition> DEFINITIONS = Map.of(
            "IIP-SSO01-cg-sp-01", new Definition(SamlSchemaConformanceCase.Rule.AUTHN_REQUEST, TargetRole.SP),
            "IIP-SSO01-dv-idp-01", new Definition(SamlSchemaConformanceCase.Rule.RESPONSE, TargetRole.IDP));
    private final String id;
    private final TranscriptContentReader content;

    public SamlSchemaConformanceTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!DEFINITIONS.containsKey(id)) throw new IllegalArgumentException("Unapproved schema case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return DEFINITIONS.get(id).role(); }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive schema cases require a complete Transcript");
        return new CaseStep.Finish(new SamlSchemaConformanceCase(DEFINITIONS.get(id).rule()).evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive schema cases finish during start");
    }
}
