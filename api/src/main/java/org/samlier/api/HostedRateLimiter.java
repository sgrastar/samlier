package org.samlier.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Small single-node limiter for the hosted deployment; upstream limits remain recommended. */
final class HostedRateLimiter {
    private final Clock clock;
    private final Map<String, ArrayDeque<Instant>> events = new HashMap<>();

    HostedRateLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized void requireAllowed(String category, String client, int limit, Duration window) {
        if (category == null || category.isBlank() || client == null || client.isBlank()) {
            throw new IllegalArgumentException("Rate-limit identity must not be blank");
        }
        if (limit < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Invalid rate-limit policy");
        }
        var now = clock.instant();
        var cutoff = now.minus(window);
        var queue = events.computeIfAbsent(category + "\n" + client, ignored -> new ArrayDeque<>());
        while (!queue.isEmpty() && !queue.peekFirst().isAfter(cutoff)) queue.removeFirst();
        if (queue.size() >= limit) throw new RateLimitExceeded("Too many hosted requests; retry later");
        queue.addLast(now);
    }

    static final class RateLimitExceeded extends RuntimeException {
        RateLimitExceeded(String message) { super(message); }
    }
}
