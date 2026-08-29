package org.samlier.core.caseexec;

import java.time.Clock;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.run.Reachability;
import org.samlier.core.transcript.TranscriptRecorder;

public interface CaseContext {
    String runId();
    TargetRole targetRole();
    Clock clock();
    TestPlan.Parameters parameters();
    Reachability reachability();
    TranscriptRecorder transcript();
    boolean transcriptComplete();
}
