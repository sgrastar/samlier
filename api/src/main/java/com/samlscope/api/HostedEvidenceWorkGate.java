package com.samlscope.api;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes evidence scans; hosted request threads are rejected instead of queued. */
final class HostedEvidenceWorkGate {
    private final ReentrantLock lock = new ReentrantLock(true);

    <T> T executeManual(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        try {
            if (!lock.tryLock(0, TimeUnit.NANOSECONDS)) {
                throw new Busy("Evidence reconciliation is busy; retry later");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Busy("Evidence reconciliation was interrupted; retry later");
        }
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    void executeAutomatic(Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        lock.lock();
        try {
            operation.run();
        } finally {
            lock.unlock();
        }
    }

    static final class Busy extends RuntimeException {
        Busy(String message) {
            super(message);
        }
    }
}
