package com.samlscope.runner.cases;

import java.util.function.Function;
import java.util.ArrayList;
import com.samlscope.core.plan.TargetRole;
import com.samlscope.runner.TestCaseRegistry;

/** Implemented passive subset of approved M2 AUTOMATED cases. */
public final class M2AutomatedCaseRegistry {
    private M2AutomatedCaseRegistry() {}

    public static TestCaseRegistry create(Function<String, byte[]> metadata) {
        var cases = new ArrayList<com.samlscope.core.caseexec.TestCase>();
        MetadataSignatureTestCase.approvedIds().stream()
                .map(id -> new MetadataSignatureTestCase(id, metadata)).forEach(cases::add);
        bind(cases, "am", MetadataConsumerObservationTestCase.Rule.PERMITTED_IDENTITY_TRANSFORM);
        bind(cases, "an", MetadataConsumerObservationTestCase.Rule.EXCLUDED_CONTENT);
        bind(cases, "ao", MetadataConsumerObservationTestCase.Rule.OMITTED_KEY_INFO);
        return new TestCaseRegistry(cases);
    }

    private static void bind(
            ArrayList<com.samlscope.core.caseexec.TestCase> cases,
            String suffix,
            MetadataConsumerObservationTestCase.Rule rule) {
        cases.add(new MetadataConsumerObservationTestCase(
                "IIP-MD05-" + suffix + "-idp-01", TargetRole.IDP, rule));
        cases.add(new MetadataConsumerObservationTestCase(
                "IIP-MD05-" + suffix + "-sp-01", TargetRole.SP, rule));
    }
}
