package org.samlier.core;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdentifiersTest {
    @Test
    void createsOpaquePublicIdentifiers() {
        var first = Identifiers.newId("plan");
        var second = Identifiers.newId("plan");
        assertTrue(first.matches("plan_[0-9A-HJKMNP-TV-Z]{26}"));
        assertNotEquals(first, second);
    }
}
