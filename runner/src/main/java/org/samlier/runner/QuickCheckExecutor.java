package org.samlier.runner;

@FunctionalInterface
public interface QuickCheckExecutor {
    QuickCheckService.QuickCheckResult execute(String runId);
}
