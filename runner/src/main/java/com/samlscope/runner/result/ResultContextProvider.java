package com.samlscope.runner.result;

import java.util.List;
import com.samlscope.core.evaluation.CaseRun;
import com.samlscope.core.evaluation.RunResult;
import com.samlscope.core.plan.TestPlan;
import com.samlscope.core.run.TestRun;

/** Supplies publication metadata that is not itself a conformance determination. */
@FunctionalInterface
public interface ResultContextProvider {
    ResultDocumentContext context(TestRun run, TestPlan plan, List<CaseRun> cases, RunResult evaluation);
}
