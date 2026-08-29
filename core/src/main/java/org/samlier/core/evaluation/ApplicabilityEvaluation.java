package org.samlier.core.evaluation;

import java.util.List;
import java.util.Objects;

public record ApplicabilityEvaluation(
        String obligationKey,
        EffectiveResult effectiveResult,
        boolean conflict,
        Basis basis,
        List<String> evidenceRefs) {

    public ApplicabilityEvaluation {
        if (obligationKey == null || obligationKey.isBlank()) {
            throw new IllegalArgumentException("obligationKey must not be blank");
        }
        Objects.requireNonNull(effectiveResult, "effectiveResult");
        Objects.requireNonNull(basis, "basis");
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        if (basis == Basis.DECLARATION_ONLY_EXCLUSION && effectiveResult != EffectiveResult.FALSE) {
            throw new IllegalArgumentException("A declaration-only exclusion must have effectiveResult FALSE");
        }
    }

    public enum EffectiveResult { TRUE, FALSE, UNKNOWN }
    public enum Basis { DECLARED, OBSERVED, DECLARATION_ONLY_EXCLUSION }
}
