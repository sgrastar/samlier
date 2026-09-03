package com.samlscope.runner.cases;

import java.util.Objects;
import com.samlscope.core.evaluation.Outcome;

/** A server-defined attestation answer. Clients select a value; they never submit an Outcome. */
public record AttestationOption(
        String value,
        Outcome outcome,
        String reasonCode,
        String notVerifiedReason) {

    public AttestationOption {
        text(value, "value");
        Objects.requireNonNull(outcome, "outcome");
        text(reasonCode, "reasonCode");
        if (outcome == Outcome.INCONSISTENT) {
            throw new IllegalArgumentException("INCONSISTENT must be derived from conflicting evidence");
        }
        if (outcome == Outcome.NOT_VERIFIED) {
            text(notVerifiedReason, "notVerifiedReason");
        } else if (notVerifiedReason != null) {
            throw new IllegalArgumentException("notVerifiedReason is only valid for NOT_VERIFIED");
        }
    }

    public static AttestationOption of(String value, Outcome outcome, String reasonCode) {
        return new AttestationOption(value, outcome, reasonCode, null);
    }

    public static AttestationOption notVerified(String value, String reasonCode, String reason) {
        return new AttestationOption(value, Outcome.NOT_VERIFIED, reasonCode, reason);
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
