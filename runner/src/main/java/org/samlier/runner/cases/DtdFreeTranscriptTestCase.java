package org.samlier.runner.cases;

import java.util.Set;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.transcript.TranscriptContentReader;

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
