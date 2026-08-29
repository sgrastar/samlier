package org.samlier.runner.cases;

import java.util.Map;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.plan.TargetRole;

/** Executable wrappers for the approved SP request-ID and IdP response/assertion-ID cases. */
public final class SamlIdentifierTranscriptTestCase implements TestCase {
    private static final Map<String, SamlIdentifierUniquenessCase.Subject> SUBJECTS = Map.of(
            "IIP-SSO01-af-sp-01", SamlIdentifierUniquenessCase.Subject.SP_AUTHN_REQUEST,
            "IIP-SSO01-ao-idp-01", SamlIdentifierUniquenessCase.Subject.IDP_RESPONSE_AND_ASSERTION);

    private final String id;
    private final TranscriptContentReader content;

    public SamlIdentifierTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!SUBJECTS.containsKey(id)) throw new IllegalArgumentException("Unapproved identifier case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return id.contains("-idp-") ? TargetRole.IDP : TargetRole.SP; }

    @Override
    public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) {
            throw new IllegalStateException("Passive identifier cases must run against the complete Transcript");
        }
        var oracle = new SamlIdentifierUniquenessCase(SUBJECTS.get(id));
        return new CaseStep.Finish(oracle.evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive identifier cases finish during start");
    }
}
