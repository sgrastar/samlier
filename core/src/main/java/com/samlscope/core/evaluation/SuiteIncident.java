package com.samlscope.core.evaluation;

public record SuiteIncident(String kind, String caseId, String actionId, String note) {
    public SuiteIncident {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
        if (caseId == null || caseId.isBlank()) throw new IllegalArgumentException("caseId must not be blank");
    }
}
