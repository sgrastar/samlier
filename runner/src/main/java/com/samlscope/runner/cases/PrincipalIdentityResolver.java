package com.samlscope.runner.cases;

/** Resolves protocol identifiers to the principal known to the Test Plan fixture. */
@FunctionalInterface
public interface PrincipalIdentityResolver {
    Resolution resolve(String runId, Identifier identifier);

    record Identifier(String kind, String value, String format, String evidenceRef) {
        public Identifier {
            kind = require(kind, "kind");
            value = require(value, "value");
            format = format == null ? "" : format;
            evidenceRef = require(evidenceRef, "evidenceRef");
        }

        private static String require(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
            return value;
        }
    }

    enum Status { RESOLVED, NOT_SUBJECT_IDENTIFYING, UNKNOWN }

    record Resolution(Status status, String principalId) {
        public Resolution {
            status = java.util.Objects.requireNonNull(status, "status");
            if (status == Status.RESOLVED && (principalId == null || principalId.isBlank())) {
                throw new IllegalArgumentException("A resolved identifier requires principalId");
            }
            if (status != Status.RESOLVED && principalId != null) {
                throw new IllegalArgumentException("Only resolved identifiers may carry principalId");
            }
        }

        public static Resolution resolved(String principalId) { return new Resolution(Status.RESOLVED, principalId); }
        public static Resolution notSubjectIdentifying() { return new Resolution(Status.NOT_SUBJECT_IDENTIFYING, null); }
        public static Resolution unknown() { return new Resolution(Status.UNKNOWN, null); }
    }
}
