package com.samlscope.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataCacheTest {
    private static final String RUN = "run_0123456789ABCDEFGHJKMNPQRS";

    @TempDir java.nio.file.Path directory;

    @Test
    void runSnapshotKeepsTheFirstSuccessfulBytes() {
        var cache = new MetadataCache(directory);
        var first = "<EntityDescriptor version=\"one\"/>".getBytes(StandardCharsets.UTF_8);
        var later = "<EntityDescriptor version=\"two\"/>".getBytes(StandardCharsets.UTF_8);

        cache.putIfAbsent(RUN, first);
        cache.putIfAbsent(RUN, later);

        assertArrayEquals(first, cache.get(RUN));
    }

    @Test
    void legacyRunMigratesThePlanCacheOnlyOnce() {
        var cache = new MetadataCache(directory);
        var plan = "plan_0123456789ABCDEFGHJKMNPQRS";
        var legacy = "<EntityDescriptor version=\"legacy\"/>".getBytes(StandardCharsets.UTF_8);
        var refreshed = "<EntityDescriptor version=\"refreshed\"/>".getBytes(StandardCharsets.UTF_8);
        cache.put(plan, legacy);

        assertArrayEquals(legacy, cache.getRunSnapshot(RUN, plan));

        cache.put(plan, refreshed);
        assertArrayEquals(legacy, cache.getRunSnapshot(RUN, plan));
    }
}
