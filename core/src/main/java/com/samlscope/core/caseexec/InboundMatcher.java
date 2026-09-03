package com.samlscope.core.caseexec;

import java.util.Map;

public record InboundMatcher(String matcherKey, Map<String, String> criteria) {
    public InboundMatcher {
        if (matcherKey == null || matcherKey.isBlank()) {
            throw new IllegalArgumentException("matcherKey must not be blank");
        }
        criteria = Map.copyOf(criteria == null ? Map.of() : criteria);
    }
}
