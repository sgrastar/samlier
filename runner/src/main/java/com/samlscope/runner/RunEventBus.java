package com.samlscope.runner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class RunEventBus {
    private final Map<String, CopyOnWriteArrayList<Consumer<RunEvent>>> listeners = new ConcurrentHashMap<>();

    public Subscription subscribe(String runId, Consumer<RunEvent> listener) {
        listeners.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> listeners.computeIfPresent(runId, (ignored, values) -> {
            values.remove(listener);
            return values.isEmpty() ? null : values;
        });
    }

    public void publish(RunEvent event) {
        listeners.getOrDefault(event.runId(), new CopyOnWriteArrayList<>()).forEach(listener -> listener.accept(event));
    }

    @FunctionalInterface
    public interface Subscription { void close(); }
}
