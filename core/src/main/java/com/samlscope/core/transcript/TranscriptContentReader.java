package com.samlscope.core.transcript;

public interface TranscriptContentReader {
    byte[] readDecodedSaml(TranscriptEntry entry);
}
