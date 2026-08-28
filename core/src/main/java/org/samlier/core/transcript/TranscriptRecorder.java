package org.samlier.core.transcript;

import java.util.List;

public interface TranscriptRecorder {
    TranscriptEntry record(TranscriptInput input);
    List<TranscriptEntry> list(String runId);
}
