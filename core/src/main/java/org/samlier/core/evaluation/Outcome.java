package org.samlier.core.evaluation;

/** Obligation satisfaction returned by cases. This is intentionally not a Verdict. */
public enum Outcome {
    SATISFIED,
    SATISFIED_WITH_NOTE,
    VIOLATED,
    INDETERMINATE,
    INCONSISTENT,
    NOT_VERIFIED
}
