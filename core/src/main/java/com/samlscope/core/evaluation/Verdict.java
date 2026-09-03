package com.samlscope.core.evaluation;

public enum Verdict {
    FAIL(10),
    INCONSISTENT(9),
    ERROR(8),
    INDETERMINATE(7),
    NOT_VERIFIED(6),
    WARNING(5),
    PASS(4),
    NOT_SUPPORTED(3),
    NOT_OBSERVABLE(2),
    NOT_APPLICABLE(1);

    private final int severity;

    Verdict(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public static Verdict moreSevere(Verdict left, Verdict right) {
        return left.severity >= right.severity ? left : right;
    }
}
