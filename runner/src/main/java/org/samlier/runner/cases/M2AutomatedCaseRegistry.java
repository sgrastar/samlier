package org.samlier.runner.cases;

import java.util.function.Function;
import org.samlier.runner.TestCaseRegistry;

/** Implemented passive subset of approved M2 AUTOMATED cases. */
public final class M2AutomatedCaseRegistry {
    private M2AutomatedCaseRegistry() {}

    public static TestCaseRegistry create(Function<String, byte[]> metadata) {
        return new TestCaseRegistry(MetadataSignatureTestCase.approvedIds().stream()
                .map(id -> new MetadataSignatureTestCase(id, metadata)).toList());
    }
}
