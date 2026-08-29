package org.samlier.core.evaluation;

import java.util.Objects;
import org.samlier.core.evaluation.ApplicabilityEvaluation.Basis;
import org.samlier.core.evaluation.ApplicabilityEvaluation.EffectiveResult;

/** Canonical three-valued evaluation of an approved applicability predicate. */
public final class ApplicabilityEngine {
    private ApplicabilityEngine() {}

    public static ApplicabilityEvaluation evaluate(
            String obligationKey,
            String predicate,
            PredicateKind kind,
            ApplicabilityInput input) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(input, "input");

        if (input.exclusion() != null) {
            if (kind != PredicateKind.CLASSIFICATION_BASED) {
                throw new IllegalArgumentException("Only classification predicates permit declared exclusions");
            }
            return result(
                    obligationKey, predicate, kind, input, EffectiveResult.FALSE, false,
                    Basis.DECLARATION_ONLY_EXCLUSION);
        }

        if (input.observed() != null) {
            var conflict = input.declared() != null && !input.declared().equals(input.observed());
            return result(
                    obligationKey, predicate, kind, input,
                    input.observed() ? EffectiveResult.TRUE : EffectiveResult.FALSE,
                    conflict, Basis.OBSERVED);
        }

        var effective = switch (kind) {
            case CLAIM_BASED -> truth(input.declared());
            case CAPABILITY_BASED, CLASSIFICATION_BASED -> Boolean.TRUE.equals(input.declared())
                    ? EffectiveResult.TRUE
                    : EffectiveResult.UNKNOWN;
        };
        return result(obligationKey, predicate, kind, input, effective, false, Basis.DECLARED);
    }

    private static EffectiveResult truth(Boolean value) {
        if (value == null) return EffectiveResult.UNKNOWN;
        return value ? EffectiveResult.TRUE : EffectiveResult.FALSE;
    }

    private static ApplicabilityEvaluation result(
            String obligationKey,
            String predicate,
            PredicateKind kind,
            ApplicabilityInput input,
            EffectiveResult effective,
            boolean conflict,
            Basis basis) {
        return new ApplicabilityEvaluation(
                obligationKey, predicate, kind, input.declared(), input.observed(), effective, conflict, basis,
                input.evidenceRefs(), input.exclusion());
    }
}
