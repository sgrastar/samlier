package com.samlscope.api;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Loads the exact signed G1/G2 documents embedded in the application artifact. */
final class CatalogDocuments {
    private static final List<String> TEST_DEFINITIONS = List.of(
            "tests/cases.yaml", "tests/feasibility.yaml", "tests/mutants/baselines.yaml",
            "tests/mutants/catalog.yaml", "tests/mutants/control-mutants.yaml");
    private final Map<String, byte[]> bytes;

    private CatalogDocuments(Map<String, byte[]> bytes) {
        this.bytes = Map.copyOf(bytes);
    }

    static CatalogDocuments load() {
        var values = new LinkedHashMap<String, byte[]>();
        for (var path : List.of(
                "tests/coverage.yaml", "tests/specs.yaml", "tests/predicates.yaml",
                "tests/cases.yaml", "tests/feasibility.yaml", "tests/mutants/baselines.yaml",
                "tests/mutants/catalog.yaml", "tests/mutants/control-mutants.yaml")) {
            try (var stream = CatalogDocuments.class.getResourceAsStream("/catalog/" + path)) {
                if (stream == null) throw new IllegalStateException("Missing embedded approved catalog: " + path);
                values.put(path, stream.readAllBytes());
            } catch (IOException error) {
                throw new IllegalStateException("Could not read embedded approved catalog: " + path, error);
            }
        }
        return new CatalogDocuments(values);
    }

    Map<String, Object> parsed(String path) {
        var value = bytes.get(path);
        if (value == null) throw new IllegalArgumentException("Unknown embedded catalog: " + path);
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(1_000);
        var loaded = new Yaml(new SafeConstructor(options)).load(new ByteArrayInputStream(value));
        if (!(loaded instanceof Map<?, ?> source)) {
            throw new IllegalStateException("Embedded approved catalog is not an object: " + path);
        }
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, item) -> {
            if (!(key instanceof String text)) {
                throw new IllegalStateException("Embedded approved catalog has a non-string key: " + path);
            }
            result.put(text, item);
        });
        return Map.copyOf(result);
    }

    byte[] bytes(String path) { return bytes.get(path).clone(); }

    Map<String, byte[]> testDefinitions() {
        var result = new LinkedHashMap<String, byte[]>();
        TEST_DEFINITIONS.forEach(path -> result.put(path, bytes(path)));
        return Map.copyOf(result);
    }
}
