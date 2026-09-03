package com.samlscope.core.casedef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.samlscope.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import com.samlscope.core.casedef.CaseDefinitionCatalog.Milestone;
import com.samlscope.core.plan.TargetRole;

class CaseDefinitionCatalogMapperTest {
    @Test
    void mapsTheJudgmentRelevantG2FieldsWithoutInferringDefaults() {
        var catalog = CaseDefinitionCatalogMapper.fromDocument(Map.of("cases", List.of(caseValue())));
        var definition = catalog.require("IIP-G02-b-sp-01");

        assertEquals(TargetRole.SP, definition.role());
        assertEquals(ExecutionMode.CONFIG, definition.mode());
        assertEquals(Milestone.M1, definition.milestone());
        assertEquals(com.samlscope.core.caseexec.ConfigurationFailureSemantics.TEST_PRECONDITION,
                definition.configurationFailureSemantics());
        assertEquals(1, definition.variantPlan().size());
        assertEquals(1, definition.controls().size());
        assertEquals("A declaration-only oracle could miss the fault.", definition.counterexampleEn());
        assertEquals(List.of("Use the target readback surface."), definition.interpretationConstraints());
        assertEquals(List.of(definition), catalog.select(Milestone.M1, ExecutionMode.CONFIG, TargetRole.SP));
    }

    @Test
    void rejectsMissingConfigurationSemanticsAndVariantDrift() {
        var missingSemantics = new java.util.LinkedHashMap<>(caseValue());
        missingSemantics.remove("configuration_failure_semantics");
        assertThrows(IllegalArgumentException.class, () ->
                CaseDefinitionCatalogMapper.fromDocument(Map.of("cases", List.of(missingSemantics))));

        var drift = new java.util.LinkedHashMap<>(caseValue());
        drift.put("variant_scopes", Map.of());
        assertThrows(IllegalArgumentException.class, () ->
                CaseDefinitionCatalogMapper.fromDocument(Map.of("cases", List.of(drift))));
    }

    private Map<String, Object> caseValue() {
        return Map.ofEntries(
                Map.entry("id", "IIP-G02-b-sp-01"), Map.entry("obligation", "IIP-G02.b"),
                Map.entry("role", "sp"), Map.entry("mode", "CONFIG"), Map.entry("milestone", "M1"),
                Map.entry("covers_variants", List.of("IIP-G02.b#v-one")),
                Map.entry("variant_scopes", Map.of("IIP-G02.b#v-one", "owner_condition")),
                Map.entry("variant_plan", List.of(Map.of(
                        "reference", "IIP-G02.b#v-one", "applicability", "owner_condition",
                        "treatment", "verdict", "instruction_en", "Compare the value."))),
                Map.entry("variant_groups", List.of(Map.of(
                        "id", "all", "kind", "all_of", "members", List.of("IIP-G02.b#v-one"),
                        "rationale_en", "All variants apply."))),
                Map.entry("controls", List.of(Map.of(
                        "id", "positive", "kind", "positive", "fixture", "sp-core-minimal",
                        "description_en", "The baseline preserves the value.", "on_failure", "control_failed"))),
                Map.entry("counterexample_en", "A declaration-only oracle could miss the fault."),
                Map.entry("interpretation_constraints", List.of("Use the target readback surface.")),
                Map.entry("requires", Map.of("passed_cases", List.of(), "session", "none")),
                Map.entry("destroys_session", false),
                Map.entry("configuration_failure_semantics", "test_precondition"),
                Map.entry("case_digest", "sha256:" + "a".repeat(64)));
    }
}
