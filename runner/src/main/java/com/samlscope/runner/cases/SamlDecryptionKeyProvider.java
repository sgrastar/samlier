package com.samlscope.runner.cases;

import java.security.PrivateKey;
import java.util.Optional;

/** Supplies a Run-scoped in-memory decryption key; implementations must not persist it. */
@FunctionalInterface
public interface SamlDecryptionKeyProvider {
    Optional<PrivateKey> keyFor(String runId);
}
