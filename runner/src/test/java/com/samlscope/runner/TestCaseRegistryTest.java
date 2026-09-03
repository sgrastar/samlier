package com.samlscope.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.caseexec.CaseContext;
import com.samlscope.core.caseexec.CaseEvent;
import com.samlscope.core.caseexec.CaseState;
import com.samlscope.core.caseexec.CaseStep;
import com.samlscope.core.caseexec.TestCase;
import com.samlscope.core.plan.TargetRole;

class TestCaseRegistryTest {
    @Test
    void rejectsDuplicateCaseIds() {
        assertThrows(IllegalArgumentException.class, () -> new TestCaseRegistry(List.of(testCase("duplicate", TargetRole.IDP),
                testCase("duplicate", TargetRole.SP))));
    }

    @Test
    void resolvesByIdAndFiltersByRole() {
        var registry = new TestCaseRegistry(List.of(testCase("idp-case", TargetRole.IDP), testCase("sp-case", TargetRole.SP)));

        assertEquals("idp-case", registry.require("idp-case").id());
        assertEquals(List.of("sp-case"), registry.forRole(TargetRole.SP).stream().map(TestCase::id).toList());
        assertThrows(IllegalArgumentException.class, () -> registry.require("missing"));
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
