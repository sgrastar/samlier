package com.samlscope.core.evaluation;

public record CaseRun(
        String id,
        String obligationKey,
        CaseOutcome outcome,
        String suiteErrorReason) {

    public CaseRun {
        requireText(id, "id");
        requireText(obligationKey, "obligationKey");
        if ((outcome == null) == (suiteErrorReason == null)) {
            throw new IllegalArgumentException("Exactly one of outcome and suiteErrorReason is required");
        }
        if (suiteErrorReason != null && suiteErrorReason.isBlank()) {
            throw new IllegalArgumentException("suiteErrorReason must not be blank");
        }
    }

    public static CaseRun completed(String id, String obligationKey, CaseOutcome outcome) {
        return new CaseRun(id, obligationKey, outcome, null);
    }

    public static CaseRun suiteError(String id, String obligationKey, String reason) {
        return new CaseRun(id, obligationKey, null, reason);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
