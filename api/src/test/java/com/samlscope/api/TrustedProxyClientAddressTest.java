package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrustedProxyClientAddressTest {
    @Test
    void acceptsOneCanonicalAddressOnlyFromTheConfiguredProxy() {
        assertEquals("203.0.113.7",
                TrustedProxyClientAddress.resolve(
                        "172.30.0.1", "203.0.113.7", "172.30.0.1"));
        assertEquals("2001:db8:0:0:0:0:0:7",
                TrustedProxyClientAddress.resolve("::1", "2001:db8::7", "::1"));
    }

    @Test
    void ignoresForwardingHeadersFromUntrustedPeers() {
        assertEquals("198.51.100.9",
                TrustedProxyClientAddress.resolve(
                        "198.51.100.9", "203.0.113.7", "172.30.0.1"));
    }

    @Test
    void rejectsSpoofedChainsAndNonNumericValues() {
        assertEquals("127.0.0.1",
                TrustedProxyClientAddress.resolve(
                        "127.0.0.1", "203.0.113.7, 198.51.100.1", "127.0.0.1"));
        assertEquals("127.0.0.1",
                TrustedProxyClientAddress.resolve(
                        "127.0.0.1", "attacker.example", "127.0.0.1"));
    }
}
