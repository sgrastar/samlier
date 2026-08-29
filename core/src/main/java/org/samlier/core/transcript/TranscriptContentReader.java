package org.samlier.core.transcript;

public interface TranscriptContentReader {
    byte[] readDecodedSaml(TranscriptEntry entry);
}
