package com.samlscope.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActiveProbeCorrelationTest {
    @Test
    void roundTripsTheOpaqueCorrelationWithinTheBindingLimit() {
        var run = "run_0123456789ABCDEFGHJKMNPQRS";
        var action = "action_0123456789abcdef0123456789abcdef";

        var encoded = ActiveProbeCorrelation.encode(run, action);
        var parsed = ActiveProbeCorrelation.parse(encoded).orElseThrow();

        assertEquals(run, parsed.runId());
        assertEquals(action, parsed.actionId());
        assertTrue(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 80);
    }

    @Test
    void rejectsAmbiguousAndOversizedInboundCorrelations() {
        assertTrue(ActiveProbeCorrelation.parse("sp1:run:action:extra").isEmpty());
        assertTrue(ActiveProbeCorrelation.parse("sp1::action").isEmpty());
        assertTrue(ActiveProbeCorrelation.parse("sp1:run:").isEmpty());
        assertTrue(ActiveProbeCorrelation.parse("sp1:run:" + "a".repeat(100)).isEmpty());
    }
}
