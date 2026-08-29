package org.samlier.core.evaluation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Approved applicability predicate definitions used by the runtime evaluator. */
public record PredicateCatalog(List<Definition> definitions) {
    public PredicateCatalog {
        definitions = List.copyOf(definitions == null ? List.of() : definitions);
        var keys = new LinkedHashMap<String, Definition>();
        for (var definition : definitions) {
            Objects.requireNonNull(definition, "definition");
            if (keys.put(definition.key(), definition) != null) {
                throw new IllegalArgumentException("Duplicate predicate key: " + definition.key());
            }
        }
    }

    public Map<String, Definition> byKey() {
        var result = new LinkedHashMap<String, Definition>();
        for (var definition : definitions) result.put(definition.key(), definition);
        return Map.copyOf(result);
    }

    public record Definition(
            String key,
            PredicateKind kind,
            List<String> declaredSources,
            List<String> observedSources,
            ConflictPolicy onConflict,
            DeclarationOnlyExclusion exclusion) {

        public Definition {
            requireText(key, "key");
            Objects.requireNonNull(kind, "kind");
            declaredSources = textList(declaredSources, "declaredSources");
            observedSources = textList(observedSources, "observedSources");
            Objects.requireNonNull(onConflict, "onConflict");
            if (kind == PredicateKind.CLASSIFICATION_BASED && exclusion == null) {
                throw new IllegalArgumentException(
                        "Classification predicate requires a declaration-only exclusion: " + key);
            }
            if (kind != PredicateKind.CLASSIFICATION_BASED && exclusion != null) {
                throw new IllegalArgumentException(
                        "Only classification predicates permit a declaration-only exclusion: " + key);
            }
        }
    }

    public record DeclarationOnlyExclusion(boolean allowed, boolean requiresReason, String statement) {
        public DeclarationOnlyExclusion {
            if (!allowed) throw new IllegalArgumentException("Declaration-only exclusion must be allowed");
            if (!requiresReason) throw new IllegalArgumentException("Declaration-only exclusion must require a reason");
            requireText(statement, "statement");
        }
    }

    public enum ConflictPolicy { INCONSISTENT }

    private static List<String> textList(List<String> values, String name) {
        var copy = List.copyOf(values == null ? List.of() : values);
        for (var value : copy) requireText(value, name);
        return copy;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
