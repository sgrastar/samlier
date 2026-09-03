package com.samlscope.core.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.samlscope.core.evaluation.PredicateCatalog.ConflictPolicy;
import com.samlscope.core.evaluation.PredicateCatalog.DeclarationOnlyExclusion;
import com.samlscope.core.evaluation.PredicateCatalog.Definition;

/** Strictly maps a parsed approved predicate document into the runtime model. */
public final class PredicateCatalogMapper {
    private PredicateCatalogMapper() {}

    public static PredicateCatalog fromDocument(Map<String, ?> document) {
        var values = object(document.get("predicates"), "predicates");
        var definitions = new ArrayList<Definition>();
        for (var entry : values.entrySet()) {
            var value = object(entry.getValue(), "predicate " + entry.getKey());
            definitions.add(new Definition(
                    entry.getKey(),
                    parse(PredicateKind.class, text(value.get("kind"), entry.getKey() + ".kind")),
                    textList(value.get("declared"), entry.getKey() + ".declared"),
                    textList(value.get("observed"), entry.getKey() + ".observed"),
                    parse(ConflictPolicy.class, text(value.get("on_conflict"), entry.getKey() + ".on_conflict")),
                    exclusion(value.get("declaration_only_exclusion"), entry.getKey())));
        }
        if (definitions.isEmpty()) throw new IllegalArgumentException("predicate catalog has no definitions");
        return new PredicateCatalog(definitions);
    }

    private static DeclarationOnlyExclusion exclusion(Object value, String key) {
        if (value == null) return null;
        var exclusion = object(value, key + ".declaration_only_exclusion");
        return new DeclarationOnlyExclusion(
                bool(exclusion.get("allowed"), key + ".declaration_only_exclusion.allowed"),
                bool(exclusion.get("requires_reason"), key + ".declaration_only_exclusion.requires_reason"),
                text(exclusion.get("statement_en"), key + ".declaration_only_exclusion.statement_en"));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + value, invalid);
        }
    }

    private static Map<String, ?> object(Object value, String name) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(name + " must be an object");
        var result = new LinkedHashMap<String, Object>();
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(name + " contains a non-string key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<String> textList(Object value, String name) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(name + " must be a list");
        var result = new ArrayList<String>();
        for (var item : list) result.add(text(item, name));
        return List.copyOf(result);
    }

    private static String text(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    private static boolean bool(Object value, String name) {
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException(name + " must be a boolean");
        return result;
    }
}
