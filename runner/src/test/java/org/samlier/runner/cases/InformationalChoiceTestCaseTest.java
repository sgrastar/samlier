package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;

class InformationalChoiceTestCaseTest {
    @Test
    void recordsAnOptionalChoiceWithoutRequestingAnOperatorVerdict() {
        var testCase = new InformationalChoiceTestCase("optional-case", TargetRole.IDP);
        var finish = assertInstanceOf(CaseStep.Finish.class, testCase.start(context()));

        assertEquals(Outcome.SATISFIED_WITH_NOTE, finish.outcome().outcome());
        assertEquals(false, finish.outcome().details().get("operator_verdict_requested"));
        assertFalse(AttestationPrompt.class.isInstance(testCase));
    }

    private CaseContext context() {
        return new CaseContext() {
            @Override public String runId() { return "run_0123456789ABCDEFGHJKMNPQRS"; }
            @Override public TargetRole targetRole() { return TargetRole.IDP; }
            @Override public Clock clock() { return Clock.systemUTC(); }
            @Override public org.samlier.core.plan.TestPlan.Parameters parameters() { return null; }
            @Override public org.samlier.core.plan.TestPlan.Interaction interaction() {
                return org.samlier.core.plan.TestPlan.Interaction.defaults();
            }
            @Override public org.samlier.core.run.Reachability reachability() { return null; }
            @Override public org.samlier.core.transcript.TranscriptRecorder transcript() { return null; }
            @Override public boolean transcriptComplete() { return false; }
        };
    }
}
