package com.samlscope.runner.access;

@FunctionalInterface
public interface ManagementSessionExecutor {
    RunAccessService.ManagementSession exchange(String runId, String accessToken);
}
