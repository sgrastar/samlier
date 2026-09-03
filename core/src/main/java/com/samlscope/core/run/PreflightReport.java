package com.samlscope.core.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PreflightReport(
        String runId,
        Instant checkedAt,
        Reachability targetToSuiteReachability,
        List<Check> checks,
        Map<String, Object> observations) {
    public PreflightReport {
        checks = List.copyOf(checks == null ? List.of() : checks);
        observations = Map.copyOf(observations == null ? Map.of() : observations);
    }

    public boolean hasFailure() { return checks.stream().anyMatch(c -> c.status() == Status.FAIL); }

    public enum Status { PASS, WARNING, FAIL, NOT_CHECKED }

    public record Check(String code, Status status, String message) {}
}
