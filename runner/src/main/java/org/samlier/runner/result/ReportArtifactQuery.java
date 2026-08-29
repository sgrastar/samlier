package org.samlier.runner.result;

@FunctionalInterface
public interface ReportArtifactQuery {
    byte[] requireReport(String runId);
}
