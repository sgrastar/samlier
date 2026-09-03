package com.samlscope.runner.result;

@FunctionalInterface
public interface ReportArtifactQuery {
    byte[] requireReport(String runId);
}
