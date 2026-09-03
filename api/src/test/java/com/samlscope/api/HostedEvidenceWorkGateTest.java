package com.samlscope.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HostedEvidenceWorkGateTest {
    @Test
    void rejectsConcurrentManualWorkWithoutQueuingARequestThread() throws Exception {
        var gate = new HostedEvidenceWorkGate();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> gate.executeManual(() -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return "done";
            }));
            assertEquals(true, entered.await(2, TimeUnit.SECONDS));

            assertThrows(HostedEvidenceWorkGate.Busy.class,
                    () -> gate.executeManual(() -> "must-not-run"));
            release.countDown();
            assertEquals("done", first.get());
        }
    }

    @Test
    void automaticAndManualWorkShareTheSameGlobalSlot() throws Exception {
        var gate = new HostedEvidenceWorkGate();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var automatic = executor.submit(() -> gate.executeAutomatic(() -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }));
            assertEquals(true, entered.await(2, TimeUnit.SECONDS));

            assertThrows(HostedEvidenceWorkGate.Busy.class,
                    () -> gate.executeManual(() -> "must-not-run"));
            release.countDown();
            automatic.get();
        }
    }
}
