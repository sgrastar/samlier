package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class HostedRateLimiterTest {
    @Test
    void enforcesEachWindowAndIdentityIndependently() {
        var clock = new MutableClock(Instant.parse("2026-08-29T00:00:00Z"));
        var limiter = new HostedRateLimiter(clock);
        limiter.requireAllowed("plan", "client-a", 2, Duration.ofMinutes(1));
        limiter.requireAllowed("plan", "client-a", 2, Duration.ofMinutes(1));
        assertThrows(HostedRateLimiter.RateLimitExceeded.class,
                () -> limiter.requireAllowed("plan", "client-a", 2, Duration.ofMinutes(1)));
        assertDoesNotThrow(() -> limiter.requireAllowed("plan", "client-b", 2, Duration.ofMinutes(1)));
        clock.instant = clock.instant.plus(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> limiter.requireAllowed("plan", "client-a", 2, Duration.ofMinutes(1)));
    }

    @Test
    void combinedAdmissionDoesNotConsumeAnyTokenWhenOneLimitRejects() {
        var limiter = new HostedRateLimiter(
                Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneId.of("UTC")));
        var window = Duration.ofMinutes(1);
        limiter.requireAllowedTogether(
                new HostedRateLimiter.Rule("run", "run-a", 1, window),
                new HostedRateLimiter.Rule("global", "service", 2, window));

        assertThrows(HostedRateLimiter.RateLimitExceeded.class, () ->
                limiter.requireAllowedTogether(
                        new HostedRateLimiter.Rule("run", "run-a", 1, window),
                        new HostedRateLimiter.Rule("global", "service", 2, window)));

        assertDoesNotThrow(() -> limiter.requireAllowedTogether(
                new HostedRateLimiter.Rule("run", "run-b", 1, window),
                new HostedRateLimiter.Rule("global", "service", 2, window)));
        assertThrows(HostedRateLimiter.RateLimitExceeded.class, () ->
                limiter.requireAllowedTogether(
                        new HostedRateLimiter.Rule("run", "run-c", 1, window),
                        new HostedRateLimiter.Rule("global", "service", 2, window)));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
