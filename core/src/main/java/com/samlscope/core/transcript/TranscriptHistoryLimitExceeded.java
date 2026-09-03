package com.samlscope.core.transcript;

/** Signals that a complete bounded Transcript snapshot cannot be produced without truncation. */
public final class TranscriptHistoryLimitExceeded extends RuntimeException {
    public TranscriptHistoryLimitExceeded(String runId, int maximumEntries) {
        super("Transcript " + runId + " exceeds the automatic reconciliation limit of "
                + maximumEntries + " entries");
    }

    public TranscriptHistoryLimitExceeded(String runId, long decodedBytes, long maximumDecodedBytes) {
        super("Transcript " + runId + " has " + decodedBytes
                + " decoded SAML bytes, exceeding the automatic reconciliation limit of "
                + maximumDecodedBytes);
    }
}
