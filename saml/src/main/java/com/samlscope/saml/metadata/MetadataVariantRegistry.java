package com.samlscope.saml.metadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Plan-scoped selection behind a stable metadata URL. */
public final class MetadataVariantRegistry {
    private final Map<String, String> selections = new ConcurrentHashMap<>();

    public void select(String entityId, String variantId) {
        if (entityId == null || entityId.isBlank() || variantId == null || variantId.isBlank()) {
            throw new IllegalArgumentException("entityId and variantId are required");
        }
        selections.put(entityId, variantId);
    }

    public String selected(String entityId) {
        var value = selections.get(entityId);
        if (value == null) throw new IllegalStateException("No metadata variant selected for " + entityId);
        return value;
    }
}
