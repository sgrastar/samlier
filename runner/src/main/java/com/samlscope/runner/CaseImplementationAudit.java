package com.samlscope.runner;

import java.util.LinkedHashSet;
import java.util.Objects;
import com.samlscope.core.casedef.CaseDefinitionCatalog;
import com.samlscope.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import com.samlscope.core.casedef.CaseDefinitionCatalog.Milestone;

/** Fails startup when an implementation registry drifts from its approved G2 slice. */
public final class CaseImplementationAudit {
    private CaseImplementationAudit() {}

    public static void requireExact(
            CaseDefinitionCatalog definitions,
            TestCaseRegistry implementations,
            Milestone milestone,
            ExecutionMode mode) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(implementations, "implementations");
        Objects.requireNonNull(milestone, "milestone");
        Objects.requireNonNull(mode, "mode");
        var expected = new LinkedHashSet<String>();
        definitions.cases().stream()
                .filter(value -> value.milestone() == milestone && value.mode() == mode)
                .forEach(value -> expected.add(value.id()));
        var actual = new LinkedHashSet<>(implementations.ids());
        if (!expected.equals(actual)) {
            var missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            var extra = new LinkedHashSet<>(actual);
            extra.removeAll(expected);
            throw new IllegalStateException(
                    "Case implementation mismatch for " + milestone + "/" + mode
                            + ": missing=" + missing + ", extra=" + extra);
        }
        for (var testCase : implementations.ids().stream().map(implementations::require).toList()) {
            var approved = definitions.require(testCase.id());
            if (approved.role() != testCase.role()) {
                throw new IllegalStateException("Case role differs from approved G2 design: " + testCase.id());
            }
        }
    }
}
