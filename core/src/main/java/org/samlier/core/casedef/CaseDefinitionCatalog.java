package org.samlier.core.casedef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.samlier.core.caseexec.ConfigurationFailureSemantics;
import org.samlier.core.plan.TargetRole;

/** Runtime-safe projection of the signed G2 case catalog. */
public final class CaseDefinitionCatalog {
    private final List<CaseDefinition> cases;
    private final Map<String, CaseDefinition> byId;

    public CaseDefinitionCatalog(List<CaseDefinition> cases) {
        this.cases = List.copyOf(cases == null ? List.of() : cases);
        var indexed = new LinkedHashMap<String, CaseDefinition>();
        for (var definition : this.cases) {
            Objects.requireNonNull(definition, "case definition");
            if (indexed.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate case definition: " + definition.id());
            }
        }
        byId = Map.copyOf(indexed);
    }

    public List<CaseDefinition> cases() { return cases; }
    public Map<String, CaseDefinition> byId() { return byId; }

    public CaseDefinition require(String id) {
        var value = byId.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown approved case ID: " + id);
        return value;
    }

    public List<CaseDefinition> select(Milestone milestone, ExecutionMode mode, TargetRole role) {
        return cases.stream()
                .filter(value -> value.milestone() == milestone)
                .filter(value -> value.mode() == mode)
                .filter(value -> value.role() == role)
                .toList();
    }

    public enum ExecutionMode { AUTOMATED, BROWSER, CONFIG, ATTESTED }
    public enum Milestone { M1, M2, M3 }
    public enum VariantTreatment { VERDICT, CONTROL, INFORMATIONAL, OUT_OF_SCOPE }
    public enum VariantScope { OWNER_CONDITION, LINKED_CONDITION }
    public enum GroupKind { ALL_OF, ONE_OF, ONE_OF_AVAILABLE }
    public enum ControlKind { POSITIVE, NEGATIVE, INFORMATIONAL }

    public record CaseDefinition(
            String id,
            String obligation,
            TargetRole role,
            ExecutionMode mode,
            Milestone milestone,
            List<String> coversVariants,
            Map<String, VariantScope> variantScopes,
            List<VariantInstruction> variantPlan,
            List<VariantGroup> variantGroups,
            List<Control> controls,
            String counterexampleEn,
            List<String> interpretationConstraints,
            Requirements requires,
            boolean destroysSession,
            ConfigurationFailureSemantics configurationFailureSemantics,
            String caseDigest) {
        public CaseDefinition {
            text(id, "id");
            text(obligation, "obligation");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(milestone, "milestone");
            coversVariants = List.copyOf(coversVariants == null ? List.of() : coversVariants);
            variantScopes = Map.copyOf(variantScopes == null ? Map.of() : variantScopes);
            variantPlan = List.copyOf(variantPlan == null ? List.of() : variantPlan);
            variantGroups = List.copyOf(variantGroups == null ? List.of() : variantGroups);
            controls = List.copyOf(controls == null ? List.of() : controls);
            text(counterexampleEn, "counterexampleEn");
            interpretationConstraints = List.copyOf(
                    interpretationConstraints == null ? List.of() : interpretationConstraints);
            Objects.requireNonNull(requires, "requires");
            if (mode == ExecutionMode.CONFIG && configurationFailureSemantics == null) {
                throw new IllegalArgumentException("CONFIG case has no configuration failure semantics: " + id);
            }
            if (mode != ExecutionMode.CONFIG && configurationFailureSemantics != null) {
                throw new IllegalArgumentException("Non-CONFIG case has configuration failure semantics: " + id);
            }
            if (!variantScopes.keySet().equals(new java.util.LinkedHashSet<>(coversVariants))) {
                throw new IllegalArgumentException("Variant scope set does not match covered variants: " + id);
            }
            var planned = variantPlan.stream().map(VariantInstruction::reference)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            if (!planned.equals(new java.util.LinkedHashSet<>(coversVariants))) {
                throw new IllegalArgumentException("Variant plan does not match covered variants: " + id);
            }
            digest(caseDigest, "caseDigest");
        }
    }

    public record VariantInstruction(
            String reference, VariantScope applicability, VariantTreatment treatment, String instructionEn) {
        public VariantInstruction {
            text(reference, "variant reference");
            Objects.requireNonNull(applicability, "applicability");
            Objects.requireNonNull(treatment, "treatment");
            text(instructionEn, "instructionEn");
        }
    }

    public record VariantGroup(String id, GroupKind kind, List<String> members, String rationaleEn) {
        public VariantGroup {
            text(id, "variant group id");
            Objects.requireNonNull(kind, "kind");
            members = List.copyOf(members == null ? List.of() : members);
            if (members.isEmpty()) throw new IllegalArgumentException("Variant group must not be empty");
            text(rationaleEn, "rationaleEn");
        }
    }

    public record Control(String id, ControlKind kind, String fixture, String descriptionEn, String onFailure) {
        public Control {
            text(id, "control id");
            Objects.requireNonNull(kind, "kind");
            text(fixture, "fixture");
            text(descriptionEn, "descriptionEn");
            text(onFailure, "onFailure");
            if (!"control_failed".equals(onFailure)) {
                throw new IllegalArgumentException("Control failures must remain Suite-side incidents");
            }
        }
    }

    public record Requirements(List<String> passedCases, String session) {
        public Requirements {
            passedCases = List.copyOf(passedCases == null ? List.of() : passedCases);
            text(session, "session");
        }
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    private static void digest(String value, String name) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
    }
}
