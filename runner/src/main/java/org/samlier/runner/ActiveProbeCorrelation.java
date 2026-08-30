package org.samlier.runner;

import java.util.Optional;

/** Opaque, binding-size-safe RelayState correlation for browser-delivered active probes. */
public final class ActiveProbeCorrelation {
    private static final String PREFIX = "sp1:";

    private ActiveProbeCorrelation() {}

    public static String encode(String runId, String actionId) {
        text(runId, "runId");
        text(actionId, "actionId");
        if (runId.indexOf(':') >= 0 || actionId.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Probe correlation values must not contain ':'");
        }
        var encoded = PREFIX + runId + ":" + actionId;
        if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 80) {
            throw new IllegalArgumentException("Probe RelayState exceeds the SAML binding limit");
        }
        return encoded;
    }

    public static Optional<Value> parse(String relayState) {
        if (relayState == null || !relayState.startsWith(PREFIX)
                || relayState.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 80) {
            return Optional.empty();
        }
        var separator = relayState.indexOf(':', PREFIX.length());
        if (separator < 0 || separator == PREFIX.length() || separator == relayState.length() - 1) {
            return Optional.empty();
        }
        var runId = relayState.substring(PREFIX.length(), separator);
        var actionId = relayState.substring(separator + 1);
        if (actionId.indexOf(':') >= 0) return Optional.empty();
        return Optional.of(new Value(runId, actionId));
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    public record Value(String runId, String actionId) {
        public Value {
            text(runId, "runId");
            text(actionId, "actionId");
        }
    }
}
