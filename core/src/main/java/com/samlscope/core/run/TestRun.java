package com.samlscope.core.run;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record TestRun(
        String id,
        String planId,
        RunStatus status,
        Reachability targetToSuiteReachability,
        Map<String, Object> context,
        Instant createdAt,
        Instant updatedAt) {
    public TestRun {
        if (id == null || id.isBlank() || planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("Run identifiers must not be blank");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(targetToSuiteReachability, "targetToSuiteReachability");
        context = Map.copyOf(context == null ? Map.of() : context);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
