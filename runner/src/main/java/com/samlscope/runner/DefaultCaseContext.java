package com.samlscope.runner;

import java.time.Clock;
import java.util.Objects;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.plan.TestPlan;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.core.run.Reachability;
import com.samlscope.core.transcript.TranscriptRecorder;

/** Immutable runtime view exposed to case implementations. */
public record DefaultCaseContext(
        String runId,
        TargetRole targetRole,
        Clock clock,
        TestPlan.Parameters parameters,
        TestPlan.Interaction interaction,
        Reachability reachability,
        TranscriptRecorder transcript,
        boolean transcriptComplete) implements CaseContext {

    public DefaultCaseContext {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        Objects.requireNonNull(targetRole, "targetRole");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(interaction, "interaction");
        Objects.requireNonNull(reachability, "reachability");
        Objects.requireNonNull(transcript, "transcript");
    }
}
