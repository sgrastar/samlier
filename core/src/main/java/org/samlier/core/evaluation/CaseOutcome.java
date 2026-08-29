package org.samlier.core.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CaseOutcome(
        Outcome outcome,
        String notVerifiedReason,
        String reasonCode,
        String reasonMessageKey,
        List<EvidenceRef> evidence,
        Map<String, Object> details) {

    public CaseOutcome {
        Objects.requireNonNull(outcome, "outcome");
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        details = Map.copyOf(details == null ? Map.of() : details);
        if (outcome == Outcome.NOT_VERIFIED) {
            requireText(notVerifiedReason, "notVerifiedReason");
        } else if (notVerifiedReason != null) {
            throw new IllegalArgumentException("notVerifiedReason is only valid for NOT_VERIFIED");
        }
        if (reasonCode != null && reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (reasonMessageKey != null && reasonMessageKey.isBlank()) {
            throw new IllegalArgumentException("reasonMessageKey must not be blank");
        }
    }

    public static CaseOutcome of(Outcome outcome, String reasonCode, List<EvidenceRef> evidence) {
        return new CaseOutcome(outcome, null, reasonCode, reasonCode, evidence, Map.of());
    }

    public static CaseOutcome notVerified(String reason, String reasonCode) {
        return new CaseOutcome(Outcome.NOT_VERIFIED, reason, reasonCode, reasonCode, List.of(), Map.of());
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
