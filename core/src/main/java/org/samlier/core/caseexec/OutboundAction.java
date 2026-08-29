package org.samlier.core.caseexec;

import java.net.URI;
import java.util.Objects;

public record OutboundAction(
        String actionId,
        OutboundKind kind,
        byte[] payload,
        URI target,
        boolean requiresEphemeralCredential) {

    public OutboundAction {
        requireText(actionId, "actionId");
        Objects.requireNonNull(kind, "kind");
        payload = payload == null ? new byte[0] : payload.clone();
        Objects.requireNonNull(target, "target");
        if (!target.isAbsolute()) throw new IllegalArgumentException("target must be absolute");
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
