package org.samlier.runner.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationArtifactDigestsTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void bindsEveryJudgmentInputAndFailsWhenOneIsMissing() throws Exception {
        for (var relative : List.of(
                "tests/coverage.yaml", "tests/cases.yaml", "tests/feasibility.yaml",
                "tests/mutants/baselines.yaml", "tests/mutants/catalog.yaml",
                "tests/mutants/control-mutants.yaml", "tests/specs.yaml")) {
            var path = directory.resolve(relative);
            Files.createDirectories(path.getParent());
            Files.writeString(path, relative);
        }
        var first = EvaluationArtifactDigests.fromRepository(directory);
        Files.writeString(directory.resolve("tests/mutants/catalog.yaml"), "changed");
        var second = EvaluationArtifactDigests.fromRepository(directory);

        assertEquals(first.coverageYaml(), second.coverageYaml());
        assertNotEquals(first.testDefinitions(), second.testDefinitions());
        Files.delete(directory.resolve("tests/specs.yaml"));
        assertThrows(IllegalArgumentException.class, () -> EvaluationArtifactDigests.fromRepository(directory));
    }
}
