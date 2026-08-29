package org.samlier.runner;

import java.time.Clock;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.run.Reachability;
import org.samlier.core.transcript.TranscriptRecorder;

/** Immutable runtime view exposed to case implementations. */
public record DefaultCaseContext(
        String runId,
        TargetRole targetRole,
        Clock clock,
        TestPlan.Parameters parameters,
        Reachability reachability,
        TranscriptRecorder transcript,
        boolean transcriptComplete) implements CaseContext {

    public DefaultCaseContext {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        Objects.requireNonNull(targetRole, "targetRole");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(reachability, "reachability");
        Objects.requireNonNull(transcript, "transcript");
    }
}
