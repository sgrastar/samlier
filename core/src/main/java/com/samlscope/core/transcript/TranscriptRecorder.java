package com.samlscope.core.transcript;

import java.util.List;

public interface TranscriptRecorder {
    TranscriptEntry record(TranscriptInput input);
    TranscriptEntry updateSamlAnalysis(
            String entryId, String correlationId, java.util.Map<String, Object> samlSummary);
    List<TranscriptEntry> list(String runId);

    /** Returns a complete snapshot or fails; implementations must never silently truncate evidence. */
    default List<TranscriptEntry> listBounded(String runId, int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
        var entries = list(runId);
        if (entries.size() > maximumEntries) {
            throw new TranscriptHistoryLimitExceeded(runId, maximumEntries);
        }
        return entries;
    }
}
