package org.samlier.core.casedef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.samlier.core.casedef.CaseDefinitionCatalog.CaseDefinition;
import org.samlier.core.casedef.CaseDefinitionCatalog.Control;
import org.samlier.core.casedef.CaseDefinitionCatalog.ControlKind;
import org.samlier.core.casedef.CaseDefinitionCatalog.ExecutionMode;
import org.samlier.core.casedef.CaseDefinitionCatalog.GroupKind;
import org.samlier.core.casedef.CaseDefinitionCatalog.Milestone;
import org.samlier.core.casedef.CaseDefinitionCatalog.Requirements;
import org.samlier.core.casedef.CaseDefinitionCatalog.VariantGroup;
import org.samlier.core.casedef.CaseDefinitionCatalog.VariantInstruction;
import org.samlier.core.casedef.CaseDefinitionCatalog.VariantScope;
import org.samlier.core.casedef.CaseDefinitionCatalog.VariantTreatment;
import org.samlier.core.caseexec.ConfigurationFailureSemantics;
import org.samlier.core.plan.TargetRole;

/** Strictly maps a parsed tests/cases.yaml document without interpreting case semantics. */
public final class CaseDefinitionCatalogMapper {
    private CaseDefinitionCatalogMapper() {}

    public static CaseDefinitionCatalog fromDocument(Map<String, ?> document) {
        var root = map(document, "document");
        var values = list(root.get("cases"), "cases");
        var definitions = new ArrayList<CaseDefinition>();
        for (var index = 0; index < values.size(); index++) {
            definitions.add(definition(map(values.get(index), "cases[" + index + "]")));
        }
        return new CaseDefinitionCatalog(definitions);
    }

    private static CaseDefinition definition(Map<String, ?> value) {
        var mode = enumeration(ExecutionMode.class, value.get("mode"), "mode");
        var variants = strings(value.get("covers_variants"), "covers_variants");
        var scopes = new LinkedHashMap<String, VariantScope>();
        map(value.get("variant_scopes"), "variant_scopes").forEach((key, scope) ->
                scopes.put(key, enumeration(VariantScope.class, scope, "variant scope")));
        var plan = new ArrayList<VariantInstruction>();
        for (var item : list(value.get("variant_plan"), "variant_plan")) {
            var variant = map(item, "variant_plan entry");
            plan.add(new VariantInstruction(
                    string(variant.get("reference"), "reference"),
                    enumeration(VariantScope.class, variant.get("applicability"), "applicability"),
                    enumeration(VariantTreatment.class, variant.get("treatment"), "treatment"),
                    string(variant.get("instruction_en"), "instruction_en")));
        }
        var groups = new ArrayList<VariantGroup>();
        for (var item : list(value.get("variant_groups"), "variant_groups")) {
            var group = map(item, "variant group");
            groups.add(new VariantGroup(
                    string(group.get("id"), "group id"),
                    enumeration(GroupKind.class, group.get("kind"), "group kind"),
                    strings(group.get("members"), "group members"),
                    string(group.get("rationale_en"), "rationale_en")));
        }
        var controls = new ArrayList<Control>();
        for (var item : list(value.get("controls"), "controls")) {
            var control = map(item, "control");
            controls.add(new Control(
                    string(control.get("id"), "control id"),
                    enumeration(ControlKind.class, control.get("kind"), "control kind"),
                    string(control.get("fixture"), "fixture"),
                    string(control.get("description_en"), "description_en"),
                    string(control.get("on_failure"), "on_failure")));
        }
        var required = map(value.get("requires"), "requires");
        var semantics = value.get("configuration_failure_semantics") == null ? null
                : enumeration(ConfigurationFailureSemantics.class,
                        value.get("configuration_failure_semantics"), "configuration_failure_semantics");
        return new CaseDefinition(
                string(value.get("id"), "id"), string(value.get("obligation"), "obligation"),
                enumeration(TargetRole.class, value.get("role"), "role"), mode,
                enumeration(Milestone.class, value.get("milestone"), "milestone"), variants, scopes,
                plan, groups, controls,
                string(value.get("counterexample_en"), "counterexample_en"),
                strings(value.get("interpretation_constraints"), "interpretation_constraints"),
                new Requirements(strings(required.get("passed_cases"), "passed_cases"),
                        string(required.get("session"), "session")),
                bool(value.get("destroys_session"), "destroys_session"), semantics,
                string(value.get("case_digest"), "case_digest"));
    }

    private static String string(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string");
        }
        return text;
    }

    private static boolean bool(Object value, String name) {
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException(name + " must be boolean");
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> map(Object value, String name) {
        if (!(value instanceof Map<?, ?> source)) throw new IllegalArgumentException(name + " must be an object");
        for (var key : source.keySet()) {
            if (!(key instanceof String)) throw new IllegalArgumentException(name + " has a non-string key");
        }
        return (Map<String, ?>) source;
    }

    private static List<?> list(Object value, String name) {
        if (!(value instanceof List<?> result)) {
            throw new IllegalArgumentException(
                    name + " must be an array, but was "
                            + (value == null ? "null" : value.getClass().getName()));
        }
        return result;
    }

    private static List<String> strings(Object value, String name) {
        var result = new ArrayList<String>();
        for (var item : list(value, name)) result.add(string(item, name + " entry"));
        return List.copyOf(result);
    }

    private static <T extends Enum<T>> T enumeration(Class<T> type, Object value, String name) {
        var text = string(value, name).toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unknown " + name + ": " + value, error);
        }
    }
}
