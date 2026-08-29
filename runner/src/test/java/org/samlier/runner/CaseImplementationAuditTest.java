package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.samlier.core.casedef.CaseDefinitionCatalog;
import org.samlier.core.casedef.CaseDefinitionCatalog.CaseDefinition;
import org.samlier.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import org.samlier.core.casedef.CaseDefinitionCatalog.Milestone;
import org.samlier.core.casedef.CaseDefinitionCatalog.Requirements;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;

class CaseImplementationAuditTest {
    @Test
    void requiresExactIdsAndRolesForTheSelectedApprovedSlice() {
        var definitions = new CaseDefinitionCatalog(List.of(definition("case-a", TargetRole.IDP)));
        assertDoesNotThrow(() -> CaseImplementationAudit.requireExact(
                definitions, new TestCaseRegistry(List.of(testCase("case-a", TargetRole.IDP))),
                Milestone.M1, ExecutionMode.AUTOMATED));
        assertThrows(IllegalStateException.class, () -> CaseImplementationAudit.requireExact(
                definitions, new TestCaseRegistry(List.of()), Milestone.M1, ExecutionMode.AUTOMATED));
        assertThrows(IllegalStateException.class, () -> CaseImplementationAudit.requireExact(
                definitions, new TestCaseRegistry(List.of(testCase("case-a", TargetRole.SP))),
                Milestone.M1, ExecutionMode.AUTOMATED));
    }

    private CaseDefinition definition(String id, TargetRole role) {
        return new CaseDefinition(
                id, "REQ.a", role, ExecutionMode.AUTOMATED, Milestone.M1, List.of(), Map.of(), List.of(),
                List.of(), List.of(), new Requirements(List.of(), "none"), false, null,
                "sha256:" + "a".repeat(64));
    }

    private TestCase testCase(String id, TargetRole role) {
        return new TestCase() {
            @Override public String id() { return id; }
            @Override public TargetRole role() { return role; }
            @Override public CaseStep start(CaseContext context) { throw new UnsupportedOperationException(); }
            @Override public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
