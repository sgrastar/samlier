package com.samlscope.runner;

@FunctionalInterface
public interface QuickCheckExecutor {
    QuickCheckService.QuickCheckResult execute(String runId);
}
