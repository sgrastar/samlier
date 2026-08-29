package org.samlier.runner.result;

import java.util.List;
import org.samlier.core.evaluation.CaseRun;
import org.samlier.core.evaluation.RunResult;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.TestRun;

/** Supplies publication metadata that is not itself a conformance determination. */
@FunctionalInterface
public interface ResultContextProvider {
    ResultDocumentContext context(TestRun run, TestPlan plan, List<CaseRun> cases, RunResult evaluation);
}
