package com.samlscope.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Small in-memory LRU for expensive deterministic response payloads. */
final class BoundedByteArrayCache {
    private final int maximumEntries;
    private final Map<String, byte[]> values = new LinkedHashMap<>(16, 0.75f, true);
    private boolean computing;

    BoundedByteArrayCache(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
        this.maximumEntries = maximumEntries;
    }

    byte[] getOrCompute(String key, Supplier<byte[]> supplier) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("cache key is required");
        Objects.requireNonNull(supplier, "supplier");
        synchronized (this) {
            var cached = values.get(key);
            if (cached != null) return cached.clone();
            if (computing) throw new Busy("another response payload is being generated");
            computing = true;
        }
        try {
            var generated = Objects.requireNonNull(supplier.get(), "generated payload").clone();
            synchronized (this) {
                values.put(key, generated);
                while (values.size() > maximumEntries) {
                    values.remove(values.keySet().iterator().next());
                }
            }
            return generated.clone();
        } finally {
            synchronized (this) {
                computing = false;
            }
        }
    }

    static final class Busy extends RuntimeException {
        private Busy(String message) { super(message); }
    }
}
