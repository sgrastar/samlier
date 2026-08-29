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

public final class SamlTimeRelationshipTranscriptTestCase implements TestCase {
    private static final Map<String, SamlTimeRelationshipCase.Rule> RULES = Map.of(
            "IIP-SSO01-cn-idp-01", SamlTimeRelationshipCase.Rule.CONDITIONS_ORDER,
            "IIP-SSO01-db-idp-01", SamlTimeRelationshipCase.Rule.CONFIRMATION_WITHIN_CONDITIONS,
            "IIP-SSO01-dc-idp-01", SamlTimeRelationshipCase.Rule.CONFIRMATION_ORDER);
    private final String id;
    private final TranscriptContentReader content;

    public SamlTimeRelationshipTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved time case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive time cases require a complete Transcript");
        return new CaseStep.Finish(new SamlTimeRelationshipCase(RULES.get(id)).evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive time cases finish during start");
    }
}
