package org.samlier.store;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.samlier.core.result.RunArtifactRepository;

/** Stores each public result beneath its validated Run identifier using an atomic replacement. */
public final class FileRunArtifactRepository implements RunArtifactRepository {
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> PRIVATE_FILE =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path directory;

    public FileRunArtifactRepository(Path dataDirectory) {
        if (dataDirectory == null) throw new IllegalArgumentException("dataDirectory must not be null");
        directory = dataDirectory.toAbsolutePath().normalize().resolve("results");
        createDirectory(directory);
    }

    @Override
    public void saveResult(String runId, byte[] resultJson) {
        validateRunId(runId);
        if (resultJson == null || resultJson.length == 0) {
            throw new IllegalArgumentException("resultJson must not be empty");
        }
        var runDirectory = directory.resolve(runId);
        createDirectory(runDirectory);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(runDirectory, ".result-", ".tmp");
            setPermissions(temporary, PRIVATE_FILE);
            Files.write(temporary, resultJson);
            var destination = runDirectory.resolve("result.json");
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            setPermissions(destination, PRIVATE_FILE);
        } catch (IOException error) {
            throw new StoreException("Could not store result artifact", error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The completed or failed operation remains authoritative; stale temp files are never read.
                }
            }
        }
    }

    @Override
    public Optional<byte[]> findResult(String runId) {
        validateRunId(runId);
        var path = directory.resolve(runId).resolve("result.json");
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException error) {
            throw new StoreException("Could not read result artifact", error);
        }
    }

    private static void validateRunId(String runId) {
        if (runId == null || !runId.matches("run_[0-9A-HJKMNP-TV-Z]{26}")) {
            throw new IllegalArgumentException("Invalid run ID");
        }
    }

    private static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
            setPermissions(path, PRIVATE_DIRECTORY);
        } catch (IOException error) {
            throw new StoreException("Could not create result artifact directory", error);
        }
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems retain their platform-specific default permissions.
        }
    }
}
