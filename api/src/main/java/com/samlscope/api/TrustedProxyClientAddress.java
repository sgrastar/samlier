package com.samlscope.api;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resolves a client IP only from the explicitly trusted local reverse-proxy hop. */
final class TrustedProxyClientAddress {
    private TrustedProxyClientAddress() {}

    static String resolve(String remoteAddress, String forwardedFor, String trustedProxyAddress) {
        var remote = numericAddress(remoteAddress);
        if (remote == null) return "unknown";
        var trustedProxy = numericAddress(trustedProxyAddress);
        if (trustedProxy == null || !remote.equals(trustedProxy)) return remote.getHostAddress();

        // deploy/Caddyfile overwrites this header with one numeric address. Reject lists and
        // non-literals so a direct local caller cannot turn parsing into DNS or spoof a chain.
        if (forwardedFor == null || forwardedFor.contains(",")) return remote.getHostAddress();
        var forwarded = numericAddress(forwardedFor.trim());
        return forwarded == null ? remote.getHostAddress() : forwarded.getHostAddress();
    }

    static String canonicalAddress(String value) {
        var address = numericAddress(value);
        if (address == null) {
            throw new IllegalArgumentException("trustedProxyAddress must be one numeric IP address");
        }
        return address.getHostAddress();
    }

    private static InetAddress numericAddress(String value) {
        if (value == null || value.isBlank()
                || !value.matches("[0-9A-Fa-f:.]+")
                || (!value.contains(".") && !value.contains(":"))) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException invalid) {
            return null;
        }
    }
}
