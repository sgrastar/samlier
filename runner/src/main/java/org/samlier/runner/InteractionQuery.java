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
            List<String> answerValues) {
        public PendingInteraction {
            answerValues = List.copyOf(answerValues == null ? List.of() : answerValues);
        }
    }

    enum Kind { BROWSER, CONFIGURATION, ATTESTATION }
}
