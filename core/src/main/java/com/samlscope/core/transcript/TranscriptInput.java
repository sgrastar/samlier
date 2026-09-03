package com.samlscope.core.transcript;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TranscriptInput(
        String runId,
        Direction direction,
        Instant timestamp,
        String correlationId,
        String method,
        String url,
        Integer status,
        Map<String, List<String>> headers,
        byte[] body,
        String contentType,
        String rawQuery,
        byte[] decodedSaml,
        Map<String, Object> samlSummary) {
    public TranscriptInput {
        headers = Map.copyOf(headers == null ? Map.of() : headers);
        body = body == null ? new byte[0] : body.clone();
        decodedSaml = decodedSaml == null ? new byte[0] : decodedSaml.clone();
        samlSummary = Map.copyOf(samlSummary == null ? Map.of() : samlSummary);
    }
}
