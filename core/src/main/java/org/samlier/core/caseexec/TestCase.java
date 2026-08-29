package org.samlier.core.caseexec;

public interface TestCase {
    String id();
    CaseStep start(CaseContext context);
    CaseStep resume(CaseContext context, CaseState state, CaseEvent event);
}
