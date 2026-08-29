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

public final class SamlStringValueTranscriptTestCase implements TestCase {
    private static final Map<String, TargetRole> DEFINITIONS = Map.of(
            "IIP-SSO01-dz-idp-01", TargetRole.IDP,
            "IIP-SSO01-dz-sp-01", TargetRole.SP);
    private final String id;
    private final TranscriptContentReader content;

    public SamlStringValueTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!DEFINITIONS.containsKey(id)) throw new IllegalArgumentException("Unapproved string case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return DEFINITIONS.get(id); }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive string cases require a complete Transcript");
        return new CaseStep.Finish(new SamlStringValueCase().evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive string cases finish during start");
    }
}
