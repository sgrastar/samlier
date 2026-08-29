package org.samlier.core.evaluation;

/** A stable reference to evidence retained outside the evaluation model. */
public record EvidenceRef(String kind, String reference) {
    public EvidenceRef {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
    }
}
