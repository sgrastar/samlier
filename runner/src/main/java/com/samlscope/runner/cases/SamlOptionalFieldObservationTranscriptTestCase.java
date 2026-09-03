package com.samlscope.runner.cases;

import java.util.Set;
import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.core.transcript.TranscriptContentReader;

public final class SamlOptionalFieldObservationTranscriptTestCase implements TestCase {
    private static final Set<String> CASE_IDS = Set.of("IIP-SSO07-a-idp-01", "IIP-SSO07-a-sp-01");
    private final String id;
    private final TranscriptContentReader content;
    private final SamlOptionalFieldObservationCase oracle;

    public SamlOptionalFieldObservationTranscriptTestCase(
            String id, TranscriptContentReader content, SamlOptionalFieldObservationCase.Selector selector) {
        if (!CASE_IDS.contains(id)) throw new IllegalArgumentException("Unapproved optional-field case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
        this.oracle = new SamlOptionalFieldObservationCase(selector);
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return id.contains("-idp-") ? TargetRole.IDP : TargetRole.SP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Optional-field observation requires a complete Transcript");
        return new CaseStep.Finish(oracle.evaluate(
                TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Optional-field observation finishes during start");
    }
}
