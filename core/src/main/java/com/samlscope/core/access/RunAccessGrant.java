package com.samlscope.core.access;

import java.time.Instant;

/** Contains only irreversible token digests; raw management credentials are never persisted. */
public record RunAccessGrant(
        String runId,
        String accessTokenHash,
        String sessionTokenHash,
        String csrfTokenHash,
        Instant updatedAt,
        boolean revoked) {
    public RunAccessGrant {
        text(runId, "runId");
        digest(accessTokenHash, "accessTokenHash");
        if ((sessionTokenHash == null) != (csrfTokenHash == null)) {
            throw new IllegalArgumentException("Session and CSRF digests must be present together");
        }
        if (sessionTokenHash != null) digest(sessionTokenHash, "sessionTokenHash");
        if (csrfTokenHash != null) digest(csrfTokenHash, "csrfTokenHash");
        java.util.Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private static void digest(String value, String name) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
    }
}
