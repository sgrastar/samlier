package org.samlier.runner;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.net.URI;
import java.time.Instant;
import java.util.List;

@FunctionalInterface
public interface InteractionQuery {
    List<PendingInteraction> pending(String runId);

    record PendingInteraction(
            String caseId,
            Kind kind,
            String promptKey,
            String promptEn,
            URI startUrl,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Instant expiresAt,
            List<String> answerValues,
            CompletionMode completionMode) {
        public PendingInteraction {
            answerValues = List.copyOf(answerValues == null ? List.of() : answerValues);
            if (completionMode == null) throw new IllegalArgumentException("completionMode is required");
        }
    }

    enum Kind { BROWSER, CONFIGURATION, ATTESTATION }
    enum CompletionMode {
        OPERATOR,
        TRANSCRIPT,
        TRANSCRIPT_OR_OPERATOR
    }
}
