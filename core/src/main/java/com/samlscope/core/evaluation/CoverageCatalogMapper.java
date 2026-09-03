package com.samlscope.core.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.samlscope.core.evaluation.CoverageCatalog.Obligation;
import com.samlscope.core.evaluation.CoverageCatalog.ProfileScope;
import com.samlscope.core.evaluation.CoverageCatalog.Testability;
import com.samlscope.core.plan.TargetRole;

/** Strictly maps a parsed approved coverage document into the runtime evaluation model. */
public final class CoverageCatalogMapper {
    private CoverageCatalogMapper() {}

    public static CoverageCatalog fromDocument(Map<String, ?> document) {
        var requirements = list(document.get("requirements"), "requirements");
        var obligations = new ArrayList<Obligation>();
        for (var requirementValue : requirements) {
            var requirement = object(requirementValue, "requirement");
            var requirementId = text(requirement.get("id"), "requirement.id");
            for (var obligationValue : list(requirement.get("obligations"), requirementId + ".obligations")) {
                obligations.add(obligation(requirementId, object(obligationValue, "obligation")));
            }
        }
        if (obligations.isEmpty()) throw new IllegalArgumentException("coverage catalog has no obligations");
        return new CoverageCatalog(obligations);
    }

    private static Obligation obligation(String requirementId, Map<String, ?> value) {
        var key = text(value.get("key"), "obligation.key");
        if (!key.startsWith(requirementId + ".")) {
            throw new IllegalArgumentException("Obligation key does not belong to " + requirementId + ": " + key);
        }
        var roles = new ArrayList<TargetRole>();
        for (var role : list(value.get("roles"), key + ".roles")) {
            roles.add(parse(TargetRole.class, text(role, key + ".role")));
        }
        if (roles.isEmpty()) throw new IllegalArgumentException(key + " has no roles");
        var assignments = object(value.get("level_assignment"), key + ".level_assignment");
        ProfileScope scope = null;
        for (var role : roles) {
            var assigned = assignments.get(role.name().toLowerCase(Locale.ROOT));
            if (assigned == null) throw new IllegalArgumentException(key + " has no assignment for " + role);
            var roleScope = parse(ProfileScope.class, text(assigned, key + ".level_assignment"));
            if (scope != null && scope != roleScope) {
                throw new IllegalArgumentException(
                        key + " has different profile scopes by role; split the runtime obligation");
            }
            scope = roleScope;
        }
        if (assignments.size() != roles.size()) {
            throw new IllegalArgumentException(key + " level_assignment contains a role outside roles");
        }
        return new Obligation(
                key,
                requirementId,
                parse(Rfc2119Level.class, text(value.get("level"), key + ".level")),
                roles,
                condition(value.get("condition"), key),
                parse(Testability.class, text(value.get("testability"), key + ".testability")),
                scope);
    }

    private static String condition(Object value, String key) {
        if (value == null) return null;
        var condition = object(value, key + ".condition");
        return text(condition.get("predicate"), key + ".condition.predicate");
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + value, invalid);
        }
    }

    @SuppressWarnings("unchecked")
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

    private static List<?> list(Object value, String name) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(name + " must be a list");
        return list;
    }

    private static String text(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
