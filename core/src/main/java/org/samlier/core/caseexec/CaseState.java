package org.samlier.core.caseexec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restart-safe state. Values are deeply copied and restricted to JSON-compatible types. */
public record CaseState(String phase, Map<String, Object> data) {
    public CaseState {
        if (phase == null || phase.isBlank()) throw new IllegalArgumentException("phase must not be blank");
        data = copyObject(data == null ? Map.of() : data);
    }

    public static CaseState initial() {
        return new CaseState("initial", Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyObject(Map<String, ?> source) {
        var result = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() == null) throw new IllegalArgumentException("CaseState keys must not be null");
            result.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Object copyValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long || value instanceof Double) {
            return value;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Map<?, ?> map) {
            var stringMap = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("CaseState object keys must be strings");
                }
                stringMap.put(key, copyValue(entry.getValue()));
            }
            return java.util.Collections.unmodifiableMap(stringMap);
        }
        if (value instanceof List<?> list) {
            var copy = new ArrayList<>();
            for (var item : list) copy.add(copyValue(item));
            return java.util.Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException("CaseState value is not JSON-compatible: " + value.getClass().getName());
    }
}
