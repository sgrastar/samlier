package com.samlscope.core.evaluation;

import java.time.Instant;
import java.util.List;

/** Raw declaration and observation inputs for one conditional obligation. */
public record ApplicabilityInput(
        Boolean declared,
        Boolean observed,
        List<String> evidenceRefs,
        ExclusionDeclaration exclusion) {

    public ApplicabilityInput {
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        if (observed == null && !evidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("evidenceRefs require an observed value");
        }
        if (exclusion != null && !Boolean.FALSE.equals(declared)) {
            throw new IllegalArgumentException("An exclusion requires an explicit false declaration");
        }
    }

    public record ExclusionDeclaration(String reason, String attestedBy, Instant attestedAt) {
        public ExclusionDeclaration {
            text(reason, "reason");
            text(attestedBy, "attestedBy");
            if (attestedAt == null) throw new IllegalArgumentException("attestedAt is required");
        }

        private static void text(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }
}
