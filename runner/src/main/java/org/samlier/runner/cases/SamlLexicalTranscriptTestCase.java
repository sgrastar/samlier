package org.samlier.runner.cases;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.plan.TargetRole;

/** Executable wrappers for the six approved passive URI/time lexical case designs. */
public final class SamlLexicalTranscriptTestCase implements TestCase {
    private static final Map<String, Function<java.util.List<TargetTranscriptMessages.Message>, CaseOutcome>> RULES = Map.of(
            "IIP-SSO01-ef-idp-01", new SamlUriValueCase()::evaluate,
            "IIP-SSO01-ef-sp-01", new SamlUriValueCase()::evaluate,
            "IIP-SSO01-eg-idp-01", new SamlTimeValueCase(SamlTimeValueCase.Rule.UTC_REPRESENTATION)::evaluate,
            "IIP-SSO01-eg-sp-01", new SamlTimeValueCase(SamlTimeValueCase.Rule.UTC_REPRESENTATION)::evaluate,
            "IIP-SSO01-ei-idp-01", new SamlTimeValueCase(SamlTimeValueCase.Rule.NO_LEAP_SECOND)::evaluate,
            "IIP-SSO01-ei-sp-01", new SamlTimeValueCase(SamlTimeValueCase.Rule.NO_LEAP_SECOND)::evaluate);

    private final String id;
    private final TranscriptContentReader content;

    public SamlLexicalTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!RULES.containsKey(id)) throw new IllegalArgumentException("Unapproved lexical case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return id.contains("-idp-") ? TargetRole.IDP : TargetRole.SP; }

    @Override
    public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) {
            throw new IllegalStateException("Passive lexical cases must run against the complete Transcript");
        }
        var messages = TargetTranscriptMessages.read(context.runId(), context.transcript(), content);
        return new CaseStep.Finish(RULES.get(id).apply(messages));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive lexical cases finish during start");
    }
}
