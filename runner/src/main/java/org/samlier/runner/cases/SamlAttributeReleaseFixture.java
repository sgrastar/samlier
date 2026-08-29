package org.samlier.runner.cases;

import java.util.List;

/** Expected source-side attribute state for a controlled Test Peer release fixture. */
public record SamlAttributeReleaseFixture(String name, String nameFormat, List<Value> values) {
    public SamlAttributeReleaseFixture {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        values = List.copyOf(values == null ? List.of() : values);
    }

    public sealed interface Value permits TextValue, EmptyValue, NullValue {}
    public record TextValue(String value) implements Value {
        public TextValue {
            if (value == null || value.isEmpty()) throw new IllegalArgumentException("text value must not be empty");
        }
    }
    public enum EmptyValue implements Value { INSTANCE }
    public enum NullValue implements Value { INSTANCE }
}
