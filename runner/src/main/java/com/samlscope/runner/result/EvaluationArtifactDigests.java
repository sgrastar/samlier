package com.samlscope.runner.result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Calculates the exact artifact digests recorded in every public determination. */
public final class EvaluationArtifactDigests {
    private EvaluationArtifactDigests() {}

    public static ResultDocumentContext.EvaluationComponents fromRepository(Path root) {
        if (root == null) throw new IllegalArgumentException("root must not be null");
        var normalized = root.toAbsolutePath().normalize();
        return new ResultDocumentContext.EvaluationComponents(
                digestFile(normalized.resolve("tests/coverage.yaml")),
                digestManifest(normalized, List.of(
                        "tests/cases.yaml",
                        "tests/feasibility.yaml",
                        "tests/mutants/baselines.yaml",
                        "tests/mutants/catalog.yaml",
                        "tests/mutants/control-mutants.yaml")),
                digestFile(normalized.resolve("tests/specs.yaml")), "1", "1");
    }

    public static ResultDocumentContext.EvaluationComponents fromDocuments(
            byte[] coverage, java.util.Map<String, byte[]> testDefinitions, byte[] specs) {
        if (testDefinitions == null || testDefinitions.isEmpty()) {
            throw new IllegalArgumentException("testDefinitions must not be empty");
        }
        var entries = new ArrayList<String>();
        testDefinitions.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getValue() == null) throw new IllegalArgumentException("Null test definition: " + entry.getKey());
            entries.add(entry.getKey() + "=" + digestBytes(entry.getValue()));
        });
        return new ResultDocumentContext.EvaluationComponents(
                digestBytes(coverage),
                digestBytes((String.join("\n", entries) + "\n").getBytes(StandardCharsets.UTF_8)),
                digestBytes(specs), "1", "1");
    }

    public static String digestBytes(byte[] value) {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String digestFile(Path path) {
        try {
            if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Missing evaluation artifact: " + path);
            return digestBytes(Files.readAllBytes(path));
        } catch (IOException error) {
            throw new IllegalStateException("Could not read evaluation artifact: " + path, error);
        }
    }

    private static String digestManifest(Path root, List<String> relativePaths) {
        var entries = new ArrayList<String>();
        relativePaths.stream().sorted(Comparator.naturalOrder()).forEach(relative ->
                entries.add(relative + "=" + digestFile(root.resolve(relative))));
        return digestBytes((String.join("\n", entries) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
