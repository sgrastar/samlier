package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedByteArrayCacheTest {
    @Test
    void computesOnceAndReturnsDefensiveCopies() {
        var cache = new BoundedByteArrayCache(2);
        var calls = new AtomicInteger();
        var first = cache.getOrCompute("campaign", () -> {
            calls.incrementAndGet();
            return new byte[] {1, 2, 3};
        });
        first[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, cache.getOrCompute("campaign", () -> {
            calls.incrementAndGet();
            return new byte[] {4};
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void evictsTheLeastRecentlyUsedEntryAtTheBound() {
        var cache = new BoundedByteArrayCache(2);
        var calls = new AtomicInteger();
        cache.getOrCompute("one", () -> new byte[] {1});
        cache.getOrCompute("two", () -> new byte[] {2});
        cache.getOrCompute("one", () -> new byte[] {9});
        cache.getOrCompute("three", () -> new byte[] {3});

        assertArrayEquals(new byte[] {2}, cache.getOrCompute("two", () -> {
            calls.incrementAndGet();
            return new byte[] {2};
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsAnUnrelatedMissInsteadOfQueuingBehindExpensiveGeneration() throws Exception {
        var cache = new BoundedByteArrayCache(2);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var owner = Thread.ofPlatform().start(() -> cache.getOrCompute("one", () -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return new byte[] {1};
        }));
        try {
            if (!started.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            assertThrows(BoundedByteArrayCache.Busy.class,
                    () -> cache.getOrCompute("two", () -> new byte[] {2}));
        } finally {
            release.countDown();
            owner.join();
        }
        assertArrayEquals(new byte[] {1}, cache.getOrCompute("one", () -> new byte[] {9}));
    }
}
