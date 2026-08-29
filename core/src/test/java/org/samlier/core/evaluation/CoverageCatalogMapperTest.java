package org.samlier.core.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.samlier.core.evaluation.CoverageCatalog.ProfileScope;
import org.samlier.core.evaluation.CoverageCatalog.Testability;
import org.samlier.core.plan.TargetRole;

class CoverageCatalogMapperTest {
    @Test
    void mapsLevelsRolesConditionsAndProfileScopeWithoutHardCodedJudgments() {
        var catalog = CoverageCatalogMapper.fromDocument(document(List.of(
                obligation(
                        "IIP-G03.a", List.of("idp", "sp"), "MUST_NOT", "AUTOMATED", null,
                        Map.of("idp", "core", "sp", "core")),
                obligation(
                        "IIP-G03.b", List.of("sp"), "SHOULD", "CONFIG",
                        Map.of("predicate", "supports_feature"), Map.of("sp", "full")))));

        assertEquals(2, catalog.obligations().size());
        var first = catalog.obligations().get(0);
        assertEquals(Rfc2119Level.MUST_NOT, first.level());
        assertEquals(List.of(TargetRole.IDP, TargetRole.SP), first.roles());
        assertEquals(Testability.AUTOMATED, first.testability());
        assertEquals(ProfileScope.CORE, first.profileScope());
        assertEquals(null, first.condition());
        assertEquals("supports_feature", catalog.obligations().get(1).condition());
        assertEquals(ProfileScope.FULL, catalog.obligations().get(1).profileScope());
    }

    @Test
    void failsClosedOnMissingUnknownOrAmbiguousJudgmentFields() {
        assertThrows(IllegalArgumentException.class, () -> CoverageCatalogMapper.fromDocument(document(List.of(
                obligation(
                        "IIP-G03.a", List.of("idp", "sp"), "MUST", "AUTOMATED", null,
                        Map.of("idp", "core", "sp", "full"))))));
        assertThrows(IllegalArgumentException.class, () -> CoverageCatalogMapper.fromDocument(document(List.of(
                obligation(
                        "IIP-G03.a", List.of("idp"), "REQUIREDISH", "AUTOMATED", null,
                        Map.of("idp", "core"))))));
        assertThrows(IllegalArgumentException.class, () -> CoverageCatalogMapper.fromDocument(document(List.of(
                obligation(
                        "IIP-G03.a", List.of("idp"), "MUST", "MAGIC", null,
                        Map.of("idp", "core"))))));
        assertThrows(IllegalArgumentException.class, () -> CoverageCatalogMapper.fromDocument(document(List.of(
                obligation(
                        "IIP-OTHER.a", List.of("idp"), "MUST", "AUTOMATED", null,
                        Map.of("idp", "core"))))));
    }

    private Map<String, Object> document(List<Map<String, Object>> obligations) {
        return Map.of("requirements", List.of(Map.of("id", "IIP-G03", "obligations", obligations)));
    }

    private Map<String, Object> obligation(
            String key,
            List<String> roles,
            String level,
            String testability,
            Object condition,
            Map<String, String> assignment) {
        var value = new java.util.LinkedHashMap<String, Object>();
        value.put("key", key);
        value.put("roles", roles);
        value.put("level", level);
        value.put("testability", testability);
        value.put("condition", condition);
        value.put("level_assignment", assignment);
        return value;
    }
}
