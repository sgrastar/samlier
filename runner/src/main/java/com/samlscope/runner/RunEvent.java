package com.samlscope.runner;

import java.time.Instant;
import java.util.Map;

public record RunEvent(String runId, String type, Instant timestamp, Map<String, Object> data) {
    public RunEvent {
        data = Map.copyOf(data == null ? Map.of() : data);
    }
}
