package com.samlscope.runner.cases;

import java.util.List;
import com.samlscope.core.transcript.Direction;
import com.samlscope.core.transcript.TranscriptContentReader;
import com.samlscope.core.transcript.TranscriptRecorder;

final class TargetTranscriptMessages {
    private TargetTranscriptMessages() {}

    static List<Message> read(
            String runId, TranscriptRecorder transcript, TranscriptContentReader content) {
        return transcript.list(runId).stream()
                .filter(entry -> entry.direction() == Direction.INBOUND)
                .filter(entry -> entry.decodedSamlRef() != null && entry.decodedSamlBytes() > 0)
                .map(entry -> new Message("transcript:" + entry.id(), content.readDecodedSaml(entry)))
                .toList();
    }

    record Message(String evidenceRef, byte[] xml) {
        Message {
            if (evidenceRef == null || evidenceRef.isBlank()) {
                throw new IllegalArgumentException("evidenceRef must not be blank");
            }
            if (xml == null || xml.length == 0) throw new IllegalArgumentException("xml must not be empty");
            xml = xml.clone();
        }

        @Override public byte[] xml() { return xml.clone(); }
    }
}
