package com.samlscope.runner.cases;

import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseExecutionRepository;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.core.transcript.TranscriptContentReader;

public final class SamlRequesterVersionTranscriptTestCase implements TestCase {
    public static final String CASE_ID = "IIP-SSO01-el-sp-01";
    private final TranscriptContentReader content;
    private final CaseExecutionRepository executions;

    public SamlRequesterVersionTranscriptTestCase(
            TranscriptContentReader content, CaseExecutionRepository executions) {
        this.content = Objects.requireNonNull(content, "content");
        this.executions = Objects.requireNonNull(executions, "executions");
    }

    @Override public String id() { return CASE_ID; }
    @Override public TargetRole role() { return TargetRole.SP; }

    @Override public CaseStep start(CaseContext context) {
        if (!context.transcriptComplete()) throw new IllegalStateException("Passive requester-version case requires a complete Transcript");
        return new CaseStep.Finish(new SamlRequesterVersionCase(executions).evaluate(
                context.runId(), TargetTranscriptMessages.read(context.runId(), context.transcript(), content)));
    }

    @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Passive requester-version case finishes during start");
    }
}
