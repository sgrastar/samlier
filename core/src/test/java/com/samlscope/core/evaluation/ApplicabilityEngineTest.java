package com.samlscope.core.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.ApplicabilityEvaluation.Basis;
import com.samlscope.core.evaluation.ApplicabilityEvaluation.EffectiveResult;
import com.samlscope.core.evaluation.ApplicabilityInput.ExclusionDeclaration;

class ApplicabilityEngineTest {
    @Test
    void claimBasedDeclarationsAreTheTruthValue() {
        assertEvaluation(PredicateKind.CLAIM_BASED, true, null, EffectiveResult.TRUE, false, Basis.DECLARED);
        assertEvaluation(PredicateKind.CLAIM_BASED, false, null, EffectiveResult.FALSE, false, Basis.DECLARED);
        assertEvaluation(PredicateKind.CLAIM_BASED, null, null, EffectiveResult.UNKNOWN, false, Basis.DECLARED);
    }

    @Test
    void capabilityDeclarationCannotSilentlyExcludeAnObligation() {
        assertEvaluation(PredicateKind.CAPABILITY_BASED, true, null, EffectiveResult.TRUE, false, Basis.DECLARED);
        assertEvaluation(PredicateKind.CAPABILITY_BASED, false, null, EffectiveResult.UNKNOWN, false, Basis.DECLARED);
        assertEvaluation(PredicateKind.CAPABILITY_BASED, null, null, EffectiveResult.UNKNOWN, false, Basis.DECLARED);
    }

    @Test
    void observationTakesPrecedenceAndContradictionsRemainIndependent() {
        assertEvaluation(PredicateKind.CAPABILITY_BASED, false, true, EffectiveResult.TRUE, true, Basis.OBSERVED);
        assertEvaluation(PredicateKind.CAPABILITY_BASED, true, false, EffectiveResult.FALSE, true, Basis.OBSERVED);
        assertEvaluation(PredicateKind.CAPABILITY_BASED, true, true, EffectiveResult.TRUE, false, Basis.OBSERVED);
        assertEvaluation(PredicateKind.CAPABILITY_BASED, false, false, EffectiveResult.FALSE, false, Basis.OBSERVED);
    }

    @Test
    void classificationFalseRequiresAnExplicitReasonedExclusion() {
        assertEvaluation(PredicateKind.CLASSIFICATION_BASED, false, null, EffectiveResult.UNKNOWN, false, Basis.DECLARED);
        var exclusion = new ExclusionDeclaration(
                "Target is a token translation proxy", "operator", Instant.parse("2026-08-29T00:00:00Z"));
        var result = ApplicabilityEngine.evaluate(
                "IIP-IDP13.a", "not_token_translation_proxy", PredicateKind.CLASSIFICATION_BASED,
                new ApplicabilityInput(false, null, List.of(), exclusion));
        assertEquals(EffectiveResult.FALSE, result.effectiveResult());
        assertEquals(Basis.DECLARATION_ONLY_EXCLUSION, result.basis());
        assertFalse(result.conflict());

        assertThrows(IllegalArgumentException.class, () -> ApplicabilityEngine.evaluate(
                "IIP-MD08.a", "supports_outbound_encryption", PredicateKind.CAPABILITY_BASED,
                new ApplicabilityInput(false, null, List.of(), exclusion)));
    }

    @Test
    void evidenceCannotExistWithoutAnObservationAndConflictCannotBeFabricated() {
        assertThrows(IllegalArgumentException.class, () ->
                new ApplicabilityInput(true, null, List.of("metadata:feature"), null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicabilityEvaluation(
                "REQ.a", "feature", PredicateKind.CAPABILITY_BASED, true, true,
                EffectiveResult.TRUE, true, Basis.OBSERVED, List.of("metadata:feature"), null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicabilityEvaluation(
                "REQ.a", "feature", PredicateKind.CAPABILITY_BASED, false, null,
                EffectiveResult.FALSE, false, Basis.DECLARED, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new ApplicabilityEvaluation(
                "REQ.a", "feature", PredicateKind.CAPABILITY_BASED, false, null,
                EffectiveResult.FALSE, false, Basis.DECLARATION_ONLY_EXCLUSION, List.of(),
                new ExclusionDeclaration("reason", "operator", Instant.EPOCH)));
    }

    private void assertEvaluation(
            PredicateKind kind,
            Boolean declared,
            Boolean observed,
            EffectiveResult effective,
            boolean conflict,
            Basis basis) {
        var evidence = observed == null ? List.<String>of() : List.of("evidence:predicate");
        var result = ApplicabilityEngine.evaluate(
                "REQ.a", "feature", kind, new ApplicabilityInput(declared, observed, evidence, null));
        assertEquals(effective, result.effectiveResult());
        assertEquals(conflict, result.conflict());
        assertEquals(basis, result.basis());
        assertEquals(declared, result.declared());
        assertEquals(observed, result.observed());
        if (conflict) assertTrue(result.conflict());
    }
}
