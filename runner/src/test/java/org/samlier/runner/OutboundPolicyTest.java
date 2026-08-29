package org.samlier.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class OutboundPolicyTest {
    @Test
    void hostedPolicyRejectsLoopback() {
        assertThrows(IllegalArgumentException.class,
                () -> new OutboundPolicy(false).requireAllowed(URI.create("http://127.0.0.1:8080/metadata")));
    }

    @Test
    void selfHostedPolicyAllowsInternalTargets() {
        assertDoesNotThrow(
                () -> new OutboundPolicy(true).requireAllowed(URI.create("http://127.0.0.1:8080/metadata")));
    }
}
