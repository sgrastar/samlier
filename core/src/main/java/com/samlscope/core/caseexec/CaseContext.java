package com.samlscope.core.caseexec;

import java.time.Clock;
import com.samlscope.core.plan.TestPlan;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.core.run.Reachability;
import com.samlscope.core.transcript.TranscriptRecorder;

public interface CaseContext {
    String runId();
    TargetRole targetRole();
    Clock clock();
    TestPlan.Parameters parameters();
    TestPlan.Interaction interaction();
    Reachability reachability();
    TranscriptRecorder transcript();
    boolean transcriptComplete();
}
