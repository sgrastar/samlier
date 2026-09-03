package com.samlscope.runner;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;

public final class OutboundPolicy {
    private final boolean allowPrivate;

    public OutboundPolicy(boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    public void requireAllowed(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only http and https destinations are allowed");
        }
        if (uri.getHost() == null) throw new IllegalArgumentException("Outbound URL has no host");
        if (allowPrivate) return;
        try {
            for (var address : InetAddress.getAllByName(uri.getHost())) {
                if (blocked(address)) {
                    throw new IllegalArgumentException("Hosted mode blocks private, local, and special-purpose destinations");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Outbound host could not be resolved", e);
        }
    }

    private boolean blocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        var bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            var first = bytes[0] & 0xff;
            var second = bytes[1] & 0xff;
            return first == 0 || first >= 224 || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0) || (first == 198 && (second == 18 || second == 19));
        }
        if (address instanceof Inet6Address) {
            return (bytes[0] & 0xfe) == 0xfc;
        }
        return true;
    }
}
