package com.samlscope.runner.outbox;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** One-use credential handoff. Secrets never enter CaseState or the persistent outbox. */
public final class InMemoryEphemeralCredentialProvider implements EphemeralCredentialProvider, AutoCloseable {
    private final ConcurrentHashMap<Key, byte[]> values = new ConcurrentHashMap<>();

    public void put(String runId, String actionId, byte[] credential) {
        requireText(runId, "runId");
        requireText(actionId, "actionId");
        if (credential == null || credential.length == 0) {
            throw new IllegalArgumentException("credential must not be empty");
        }
        var stored = credential.clone();
        var previous = values.put(new Key(runId, actionId), stored);
        if (previous != null) Arrays.fill(previous, (byte) 0);
    }

    @Override
    public Optional<byte[]> credentialFor(String runId, String actionId) {
        var value = values.remove(new Key(runId, actionId));
        if (value == null) return Optional.empty();
        try {
            return Optional.of(value.clone());
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }

    public void discard(String runId, String actionId) {
        var value = values.remove(new Key(runId, actionId));
        if (value != null) Arrays.fill(value, (byte) 0);
    }

    @Override
    public void close() {
        values.values().forEach(value -> Arrays.fill(value, (byte) 0));
        values.clear();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private record Key(String runId, String actionId) {}
}
