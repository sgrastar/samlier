package org.samlier.core.caseexec;

import org.samlier.core.plan.TargetRole;

public interface TestCase {
    String id();
    TargetRole role();
    CaseStep start(CaseContext context);
    CaseStep resume(CaseContext context, CaseState state, CaseEvent event);
}
