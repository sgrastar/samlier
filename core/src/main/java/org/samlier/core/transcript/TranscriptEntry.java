package org.samlier.core.transcript;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TranscriptEntry(
        String id,
        String runId,
        Direction direction,
        Instant timestamp,
        String correlationId,
        String method,
        String url,
        Integer status,
        Map<String, List<String>> headers,
        String bodyRef,
        int bodyBytes,
        String decodedSamlRef,
        int decodedSamlBytes,
        String contentType,
        String rawQuery,
        Map<String, Object> samlSummary) {}
