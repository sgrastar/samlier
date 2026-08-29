package org.samlier.core.evaluation;

import java.util.List;
import java.util.Objects;
import org.samlier.core.evaluation.ApplicabilityInput.ExclusionDeclaration;

public record ApplicabilityEvaluation(
        String obligationKey,
        String predicate,
        PredicateKind predicateKind,
        Boolean declared,
        Boolean observed,
        EffectiveResult effectiveResult,
        boolean conflict,
        Basis basis,
        List<String> evidenceRefs,
        ExclusionDeclaration exclusion) {

    public ApplicabilityEvaluation {
        if (obligationKey == null || obligationKey.isBlank()) {
            throw new IllegalArgumentException("obligationKey must not be blank");
        }
        if (predicate == null || predicate.isBlank()) {
            throw new IllegalArgumentException("predicate must not be blank");
        }
        Objects.requireNonNull(predicateKind, "predicateKind");
        Objects.requireNonNull(effectiveResult, "effectiveResult");
        Objects.requireNonNull(basis, "basis");
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        switch (basis) {
            case OBSERVED -> {
                if (exclusion != null) {
                    throw new IllegalArgumentException("Observed basis must not carry an exclusion");
                }
                if (observed == null) {
                    throw new IllegalArgumentException("Observed basis requires an observed value");
                }
                var expected = observed ? EffectiveResult.TRUE : EffectiveResult.FALSE;
                if (effectiveResult != expected) {
                    throw new IllegalArgumentException("Observed value must determine effectiveResult");
                }
            }
            case DECLARED -> {
                if (exclusion != null) {
                    throw new IllegalArgumentException("Declared basis must not carry an exclusion");
                }
                if (observed != null) {
                    throw new IllegalArgumentException("Declared basis must not carry an observed value");
                }
                var expected = predicateKind == PredicateKind.CLAIM_BASED
                        ? truth(declared)
                        : Boolean.TRUE.equals(declared) ? EffectiveResult.TRUE : EffectiveResult.UNKNOWN;
                if (effectiveResult != expected) {
                    throw new IllegalArgumentException("Declaration-only input has an invalid effectiveResult");
                }
            }
            case DECLARATION_ONLY_EXCLUSION -> {
                if (predicateKind != PredicateKind.CLASSIFICATION_BASED
                        || !Boolean.FALSE.equals(declared)
                        || observed != null
                        || exclusion == null
                        || effectiveResult != EffectiveResult.FALSE) {
                    throw new IllegalArgumentException("Invalid declaration-only exclusion");
                }
            }
        }
        if (conflict && (declared == null || observed == null || declared.equals(observed))) {
            throw new IllegalArgumentException("conflict requires contradictory declared and observed values");
        }
        if (!conflict && declared != null && observed != null && !declared.equals(observed)) {
            throw new IllegalArgumentException("Contradictory values must set conflict");
        }
    }

    public enum EffectiveResult { TRUE, FALSE, UNKNOWN }
    public enum Basis { DECLARED, OBSERVED, DECLARATION_ONLY_EXCLUSION }

    private static EffectiveResult truth(Boolean value) {
        if (value == null) return EffectiveResult.UNKNOWN;
        return value ? EffectiveResult.TRUE : EffectiveResult.FALSE;
    }
}
