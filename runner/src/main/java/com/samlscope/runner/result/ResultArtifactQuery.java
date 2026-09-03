package com.samlscope.runner.result;

@FunctionalInterface
public interface ResultArtifactQuery {
    byte[] require(String runId);
}
