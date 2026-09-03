package com.samlscope.runner;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.samlscope.core.transcript.TranscriptContentReader;
import com.samlscope.core.transcript.TranscriptEntry;
import com.samlscope.core.transcript.TranscriptInput;
import com.samlscope.core.transcript.TranscriptRecorder;

/** Records first, then asks the evidence reconciler to advance any now-complete cases. */
public final class TranscriptAutomationRecorder implements TranscriptRecorder, TranscriptContentReader {
    private static final Logger LOG = Logger.getLogger(TranscriptAutomationRecorder.class.getName());
    private final TranscriptRecorder recorder;
    private final TranscriptContentReader content;
    private final AtomicReference<Consumer<String>> listener = new AtomicReference<>();

    public TranscriptAutomationRecorder(
            TranscriptRecorder recorder, TranscriptContentReader content) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.content = Objects.requireNonNull(content, "content");
    }

    /** Installs the one Run-scoped reconciliation listener after runtime construction. */
    public void onRecorded(Consumer<String> value) {
        Objects.requireNonNull(value, "value");
        if (!listener.compareAndSet(null, value)) {
            throw new IllegalStateException("Transcript automation listener is already installed");
        }
    }

    @Override
    public TranscriptEntry record(TranscriptInput input) {
        var entry = recorder.record(input);
        notifyRecorded(entry.runId());
        return entry;
    }

    @Override
    public TranscriptEntry updateSamlAnalysis(
            String entryId, String correlationId, Map<String, Object> samlSummary) {
        var entry = recorder.updateSamlAnalysis(entryId, correlationId, samlSummary);
        notifyRecorded(entry.runId());
        return entry;
    }

    @Override public List<TranscriptEntry> list(String runId) { return recorder.list(runId); }
    @Override public byte[] readDecodedSaml(TranscriptEntry entry) { return content.readDecodedSaml(entry); }

    private void notifyRecorded(String runId) {
        var value = listener.get();
        if (value == null) return;
        try {
            value.accept(runId);
        } catch (RuntimeException failure) {
            // Evidence is safely recorded already. A later entry or management refresh retries reconciliation.
            LOG.log(Level.WARNING, "Transcript evidence reconciliation failed for " + runId, failure);
        }
    }
}
