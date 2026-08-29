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

public final class SamlAttributeValueTranscriptTestCase implements TestCase {
    private static final Map<String, SamlAttributeValueCase.Rule> RULES = Map.of(
            "IIP-SSO01-dj-idp-01", SamlAttributeValueCase.Rule.NO_VALUES,
            "IIP-SSO01-dk-idp-01", SamlAttributeValueCase.Rule.EMPTY_VALUE,
            "IIP-SSO01-dl-idp-01", SamlAttributeValueCase.Rule.NULL_VALUE,
            "IIP-SSO01-du-idp-01", SamlAttributeValueCase.Rule.DISCRETE_VALUES);
    private final String id;
    private final TranscriptContentReader content;
    private final SamlAttributeValueCase oracle;

    public SamlAttributeValueTranscriptTestCase(
            String id, TranscriptContentReader content, SamlAttributeReleaseFixture fixture) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved AttributeValue case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
        this.oracle = new SamlAttributeValueCase(RULES.get(id), fixture);
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return TargetRole.IDP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("AttributeValue cases require a complete Transcript");
        return new CaseStep.Finish(oracle.evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("AttributeValue cases finish during start");
    }
}
