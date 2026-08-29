package org.samlier.runner.result;

@FunctionalInterface
public interface ResultArtifactQuery {
    byte[] require(String runId);
}
