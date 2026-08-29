package org.samlier.runner;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;

/** Immutable execution registry keyed by the approved G2 case ID. */
public final class TestCaseRegistry {
    private final Map<String, TestCase> cases;

    public TestCaseRegistry(Collection<? extends TestCase> cases) {
        var indexed = new LinkedHashMap<String, TestCase>();
        for (var testCase : List.copyOf(cases)) {
            if (testCase == null) throw new IllegalArgumentException("TestCase must not be null");
            if (indexed.putIfAbsent(testCase.id(), testCase) != null) {
                throw new IllegalArgumentException("Duplicate TestCase ID: " + testCase.id());
            }
        }
        this.cases = Map.copyOf(indexed);
    }

    public Optional<TestCase> find(String id) {
        return Optional.ofNullable(cases.get(id));
    }

    public TestCase require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown case ID: " + id));
    }

    public List<TestCase> forRole(TargetRole role) {
        return cases.values().stream().filter(testCase -> testCase.role() == role)
                .sorted(java.util.Comparator.comparing(TestCase::id)).toList();
    }

    public Set<String> ids() {
        return cases.keySet();
    }

    public List<TestCase> all() {
        return cases.values().stream().sorted(java.util.Comparator.comparing(TestCase::id)).toList();
    }

    public static TestCaseRegistry merge(TestCaseRegistry... registries) {
        var values = new java.util.ArrayList<TestCase>();
        for (var registry : registries) values.addAll(Objects.requireNonNull(registry, "registry").all());
        return new TestCaseRegistry(values);
    }
}
