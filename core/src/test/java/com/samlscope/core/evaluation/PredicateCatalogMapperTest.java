package com.samlscope.core.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.samlscope.core.evaluation.PredicateCatalog.ConflictPolicy;

class PredicateCatalogMapperTest {
    @Test
    void mapsApprovedPredicateMetadataWithoutInterpretingSourceCombinationRules() {
        var catalog = PredicateCatalogMapper.fromDocument(Map.of("predicates", Map.of(
                "feature", predicate(
                        "CAPABILITY_BASED",
                        List.of("declared.first", "declared.second"),
                        List.of("target_emitted: example"),
                        null),
                "classification", predicate(
                        "CLASSIFICATION_BASED",
                        List.of("target.kind"),
                        List.of(),
                        Map.of(
                                "allowed", true,
                                "requires_reason", true,
                                "statement_en", "The target declared an excluded classification.")))));

        var feature = catalog.byKey().get("feature");
        assertEquals(PredicateKind.CAPABILITY_BASED, feature.kind());
        assertEquals(List.of("declared.first", "declared.second"), feature.declaredSources());
        assertEquals(List.of("target_emitted: example"), feature.observedSources());
        assertEquals(ConflictPolicy.INCONSISTENT, feature.onConflict());
        var classification = catalog.byKey().get("classification");
        assertEquals("The target declared an excluded classification.", classification.exclusion().statement());
    }

    @Test
    void failsClosedOnUnsupportedConflictPolicyOrMalformedExclusion() {
        assertThrows(IllegalArgumentException.class, () -> PredicateCatalogMapper.fromDocument(Map.of(
                "predicates", Map.of("feature", predicate(
                        "CAPABILITY_BASED", List.of(), List.of(), null, "ignore")))));
        assertThrows(IllegalArgumentException.class, () -> PredicateCatalogMapper.fromDocument(Map.of(
                "predicates", Map.of("classification", predicate(
                        "CLASSIFICATION_BASED", List.of("target.kind"), List.of(), null)))));
        assertThrows(IllegalArgumentException.class, () -> PredicateCatalogMapper.fromDocument(Map.of(
                "predicates", Map.of("feature", predicate(
                        "CAPABILITY_BASED", List.of(), List.of(), Map.of(
                                "allowed", true,
                                "requires_reason", true,
                                "statement_en", "Not permitted here."))))));
        assertThrows(IllegalArgumentException.class, () -> PredicateCatalogMapper.fromDocument(Map.of(
                "predicates", Map.of("classification", predicate(
                        "CLASSIFICATION_BASED", List.of(), List.of(), Map.of(
                                "allowed", true,
                                "requires_reason", false,
                                "statement_en", "Reason is optional."))))));
    }

    @Test
    void rejectsDuplicateDefinitionsInRuntimeModel() {
        var definition = new PredicateCatalog.Definition(
                "feature", PredicateKind.CLAIM_BASED, List.of("claim"), List.of(),
                ConflictPolicy.INCONSISTENT, null);
        assertThrows(IllegalArgumentException.class, () -> new PredicateCatalog(List.of(definition, definition)));
    }

    private Map<String, Object> predicate(
            String kind, List<String> declared, List<String> observed, Object exclusion) {
        return predicate(kind, declared, observed, exclusion, "inconsistent");
    }

    private Map<String, Object> predicate(
            String kind, List<String> declared, List<String> observed, Object exclusion, String onConflict) {
        var value = new LinkedHashMap<String, Object>();
        value.put("kind", kind);
        value.put("declared", declared);
        value.put("observed", observed);
        value.put("on_conflict", onConflict);
        if (exclusion != null) value.put("declaration_only_exclusion", exclusion);
        return value;
    }
}
