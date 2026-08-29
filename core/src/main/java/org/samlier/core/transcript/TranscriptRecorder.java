package org.samlier.core.transcript;

import java.util.List;

public interface TranscriptRecorder {
    TranscriptEntry record(TranscriptInput input);
    TranscriptEntry updateSamlAnalysis(
            String entryId, String correlationId, java.util.Map<String, Object> samlSummary);
    List<TranscriptEntry> list(String runId);
}
