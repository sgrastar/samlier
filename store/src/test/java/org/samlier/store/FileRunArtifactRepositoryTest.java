package org.samlier.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRunArtifactRepositoryTest {
    private static final String RUN_ID = "run_0123456789ABCDEFGHJKMNPQRS";

    @TempDir java.nio.file.Path directory;

    @Test
    void atomicallyReplacesAndReadsTheCanonicalResult() throws Exception {
        var repository = new FileRunArtifactRepository(directory);
        repository.saveResult(RUN_ID, "{\"version\":1}".getBytes(StandardCharsets.UTF_8));
        var replacement = "{\"version\":2}".getBytes(StandardCharsets.UTF_8);
        repository.saveResult(RUN_ID, replacement);

        assertArrayEquals(replacement, repository.findResult(RUN_ID).orElseThrow());
        try (var paths = Files.list(directory.resolve("results").resolve(RUN_ID))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void rejectsTraversalAndEmptyArtifacts() {
        var repository = new FileRunArtifactRepository(directory);
        assertThrows(IllegalArgumentException.class, () -> repository.findResult("../../outside"));
        assertThrows(IllegalArgumentException.class, () -> repository.saveResult(RUN_ID, new byte[0]));
    }
}
