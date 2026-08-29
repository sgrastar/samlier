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

public final class SamlVersionTranscriptTestCase implements TestCase {
    private static final Map<String, SamlVersionEmissionCase.Rule> RULES = Map.of(
            "IIP-SSO01-ej-idp-01", SamlVersionEmissionCase.Rule.ASSERTIONS_SUPPORTED,
            "IIP-SSO01-eq-idp-01", SamlVersionEmissionCase.Rule.NO_V1_ASSERTION_IN_V2_RESPONSE,
            "IIP-SSO01-fg-sp-01", SamlVersionEmissionCase.Rule.AUTHN_REQUEST_HIGHEST);
    private final String id;
    private final TranscriptContentReader content;

    public SamlVersionTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved version case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return id.contains("-idp-") ? TargetRole.IDP : TargetRole.SP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive version cases require a complete Transcript");
        return new CaseStep.Finish(new SamlVersionEmissionCase(RULES.get(id)).evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive version cases finish during start");
    }
}
