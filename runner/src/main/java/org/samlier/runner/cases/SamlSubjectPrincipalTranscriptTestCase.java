package org.samlier.runner.cases;

import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.transcript.TranscriptContentReader;

public final class SamlSubjectPrincipalTranscriptTestCase implements TestCase {
    public static final String CASE_ID = "IIP-SSO01-cz-idp-01";
    private final TranscriptContentReader content;
    private final PrincipalIdentityResolver resolver;

    public SamlSubjectPrincipalTranscriptTestCase(TranscriptContentReader content, PrincipalIdentityResolver resolver) {
        this.content = Objects.requireNonNull(content, "content");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive subject cases require a complete Transcript");
        return new CaseStep.Finish(new SamlSubjectPrincipalCase(resolver).evaluate(
                context.runId(), TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive subject cases finish during start");
    }
}
