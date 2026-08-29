package org.samlier.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MetadataCache {
    private final Path directory;

    public MetadataCache(Path dataDirectory) {
        this.directory = dataDirectory.resolve("target-metadata");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new StoreException("Could not create metadata cache", e);
        }
    }

    public void put(String planId, byte[] metadata) {
        try {
            Files.write(path(planId), metadata);
        } catch (IOException e) {
            throw new StoreException("Could not cache target metadata", e);
        }
    }

    public byte[] get(String planId) {
        try {
            return Files.readAllBytes(path(planId));
        } catch (IOException e) {
            throw new StoreException("Target metadata is not cached for plan " + planId, e);
        }
    }

    private Path path(String planId) {
        if (!planId.matches("[a-z]+_[0-9A-HJKMNP-TV-Z]{26}")) {
            throw new IllegalArgumentException("Invalid plan ID");
        }
        return directory.resolve(planId + ".xml");
    }
}
