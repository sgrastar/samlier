package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;

class DefaultCaseContextTest {
    @Test
    void rejectsAnIncompleteRuntimeContext() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultCaseContext(
                "", TargetRole.IDP, Clock.systemUTC(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(),
                Reachability.UNKNOWN, transcript(), false));
        assertThrows(NullPointerException.class, () -> new DefaultCaseContext(
                "run", null, Clock.systemUTC(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(),
                Reachability.UNKNOWN, transcript(), false));
    }

    private TranscriptRecorder transcript() {
        return new TranscriptRecorder() {
            @Override public TranscriptEntry record(TranscriptInput input) { throw new UnsupportedOperationException(); }
            @Override public TranscriptEntry updateSamlAnalysis(
                    String entryId, String correlationId, java.util.Map<String, Object> summary) {
                throw new UnsupportedOperationException();
            }
            @Override public java.util.List<TranscriptEntry> list(String runId) { return java.util.List.of(); }
        };
    }
}
