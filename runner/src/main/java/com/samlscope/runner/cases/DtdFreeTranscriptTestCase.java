package com.samlscope.runner.cases;

import java.util.Set;
import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.transcript.TranscriptContentReader;
import com.samlscope.core.plan.TargetRole;

/** Executable wrapper for the two role-specific G2 case IDs that share the IIP-G03.a oracle. */
public final class DtdFreeTranscriptTestCase implements TestCase {
    private static final Set<String> APPROVED_IDS = Set.of(
            "IIP-G03-a-idp-01",
            "IIP-G03-a-sp-01");

    private final String id;
    private final TranscriptContentReader content;
    private final DtdFreeTargetSamlCase oracle = new DtdFreeTargetSamlCase();

    public DtdFreeTranscriptTestCase(String id, TranscriptContentReader content) {
        if (!APPROVED_IDS.contains(id)) throw new IllegalArgumentException("Unapproved G03 case id: " + id);
        this.id = id;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    public String id() {
        return id;
    }

    @Override public TargetRole role() {
        return id.contains("-idp-") ? TargetRole.IDP : TargetRole.SP;
    }

    @Override
    public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) {
            throw new IllegalStateException("IIP-G03.a must run against the complete Transcript");
        }
        return new CaseStep.Finish(
                oracle.evaluateTranscript(context.runId(), context.transcript(), content));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("The passive G03 case finishes during start");
    }
}
