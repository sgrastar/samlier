package org.samlier.runner.cases;

import java.util.Set;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.TranscriptContentReader;

public final class SamlQualifierOmissionTranscriptTestCase implements TestCase {
    private static final Set<String> CASE_IDS = Set.of("IIP-SSO01-cy-idp-01", "IIP-SSO01-cy-sp-01");
    private final String id;
    private final TranscriptContentReader content;

    public SamlQualifierOmissionTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!CASE_IDS.contains(id)) throw new IllegalArgumentException("Unapproved qualifier case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return id.contains("-idp-") ? TargetRole.IDP : TargetRole.SP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive qualifier cases require a complete Transcript");
        return new CaseStep.Finish(new SamlQualifierOmissionCase().evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive qualifier cases finish during start");
    }
}
