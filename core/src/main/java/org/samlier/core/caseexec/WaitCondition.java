package org.samlier.core.caseexec;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public record WaitCondition(
        Kind kind,
        String promptKey,
        URI startUrl,
        InboundMatcher inboundMatcher,
        Instant expiresAt) {

    public WaitCondition {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(expiresAt, "expiresAt");
        switch (kind) {
            case BROWSER -> Objects.requireNonNull(startUrl, "startUrl");
            case CONFIG, ATTESTATION -> {
                if (promptKey == null || promptKey.isBlank()) {
                    throw new IllegalArgumentException("promptKey is required for " + kind);
                }
            }
            case INBOUND -> Objects.requireNonNull(inboundMatcher, "inboundMatcher");
        }
    }

    public enum Kind { BROWSER, CONFIG, ATTESTATION, INBOUND }
}
