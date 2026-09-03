package com.samlscope.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Small single-node limiter for the hosted deployment; upstream limits remain recommended. */
final class HostedRateLimiter {
    private static final int MAX_IDENTITIES = 10_000;
    private final Clock clock;
    private final Map<String, Bucket> events = new HashMap<>();

    HostedRateLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized void requireAllowed(String category, String client, int limit, Duration window) {
        requireAllowedTogether(new Rule(category, client, limit, window));
    }

    synchronized void requireAllowedTogether(Rule... rules) {
        if (rules == null || rules.length == 0) {
            throw new IllegalArgumentException("At least one rate-limit policy is required");
        }
        var now = clock.instant();
        var keys = new HashSet<String>();
        var admitted = new ArrayList<ArrayDeque<Instant>>();
        for (var rule : rules) {
            rule.validate();
            var key = rule.category + "\n" + rule.client;
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Duplicate rate-limit identity in one admission");
            }
            var bucket = events.get(key);
            if (bucket == null) {
                if (events.size() >= MAX_IDENTITIES) pruneExpired(now);
                if (events.size() >= MAX_IDENTITIES) {
                    throw new RateLimitExceeded("Too many hosted request identities; retry later");
                }
                bucket = new Bucket(rule.window);
                events.put(key, bucket);
            } else if (!bucket.window.equals(rule.window)) {
                throw new IllegalArgumentException("Rate-limit identity cannot change its window");
            }
            var queue = bucket.events;
            var cutoff = now.minus(rule.window);
            while (!queue.isEmpty() && !queue.peekFirst().isAfter(cutoff)) queue.removeFirst();
            if (queue.size() >= rule.limit) {
                throw new RateLimitExceeded("Too many hosted requests; retry later");
            }
            admitted.add(queue);
        }
        admitted.forEach(queue -> queue.addLast(now));
    }

    private void pruneExpired(Instant now) {
        var iterator = events.entrySet().iterator();
        while (iterator.hasNext()) {
            var bucket = iterator.next().getValue();
            var queue = bucket.events;
            var cutoff = now.minus(bucket.window);
            while (!queue.isEmpty() && !queue.peekFirst().isAfter(cutoff)) queue.removeFirst();
            if (queue.isEmpty()) iterator.remove();
        }
    }

    private static final class Bucket {
        private final Duration window;
        private final ArrayDeque<Instant> events = new ArrayDeque<>();

        private Bucket(Duration window) {
            this.window = window;
        }
    }

    record Rule(String category, String client, int limit, Duration window) {
        private void validate() {
            if (category == null || category.isBlank() || client == null || client.isBlank()) {
                throw new IllegalArgumentException("Rate-limit identity must not be blank");
            }
            if (limit < 1 || window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("Invalid rate-limit policy");
            }
        }
    }

    static final class RateLimitExceeded extends RuntimeException {
        RateLimitExceeded(String message) { super(message); }
    }
}
