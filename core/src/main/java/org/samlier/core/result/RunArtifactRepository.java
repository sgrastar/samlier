package org.samlier.core.result;

import java.util.Optional;

/** Durable storage for immutable public artifacts derived from one Test Run. */
public interface RunArtifactRepository {
    void saveResult(String runId, byte[] resultJson);

    Optional<byte[]> findResult(String runId);

    void saveReport(String runId, byte[] reportHtml);

    Optional<byte[]> findReport(String runId);
}
