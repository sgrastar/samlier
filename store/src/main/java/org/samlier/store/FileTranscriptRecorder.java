package org.samlier.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.samlier.core.Identifiers;
import org.samlier.core.transcript.TranscriptEntry;
import org.samlier.core.transcript.TranscriptContentReader;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;

public final class FileTranscriptRecorder implements TranscriptRecorder, TranscriptContentReader {
    private final SqliteDatabase database;
    private final JsonCodec json;
    private final Path directory;
    private final Redactor redactor = new Redactor();

    public FileTranscriptRecorder(SqliteDatabase database, JsonCodec json, Path dataDirectory) {
        this.database = database;
        this.json = json;
        this.directory = dataDirectory.resolve("transcripts");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new StoreException("Could not create transcript directory", e);
        }
    }

    @Override
    public TranscriptEntry record(TranscriptInput input) {
        var clean = redactor.sanitize(input.headers(), input.body(), input.contentType(), input.rawQuery(), input.url());
        var id = Identifiers.newId("tx");
        var runDirectory = safeRunDirectory(input.runId());
        try {
            Files.createDirectories(runDirectory);
            var bodyRef = writeIfPresent(runDirectory, id + ".body", clean.body());
            var samlRef = writeIfPresent(runDirectory, id + ".saml.xml", input.decodedSaml());
            var entry = new TranscriptEntry(
                    id, input.runId(), input.direction(), input.timestamp(), input.correlationId(), input.method(),
                    clean.url(), input.status(), clean.headers(), bodyRef, clean.body().length, samlRef,
                    input.decodedSaml().length, input.contentType(), clean.rawQuery(), input.samlSummary());
            try (var connection = database.open();
                 var statement = connection.prepareStatement(
                         "INSERT INTO transcript_entries(id, run_id, timestamp, document_json) VALUES(?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, input.runId());
                statement.setString(3, input.timestamp().toString());
                statement.setString(4, json.write(entry));
                statement.executeUpdate();
            }
            return entry;
        } catch (IOException | SQLException e) {
            throw new StoreException("Could not record transcript entry", e);
        }
    }

    @Override
    public List<TranscriptEntry> list(String runId) {
        var entries = new ArrayList<TranscriptEntry>();
        try (var connection = database.open();
             var statement = connection.prepareStatement(
                     "SELECT document_json FROM transcript_entries WHERE run_id = ? ORDER BY timestamp, id")) {
            statement.setString(1, runId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) entries.add(json.read(rows.getString(1), TranscriptEntry.class));
            }
            return List.copyOf(entries);
        } catch (SQLException e) {
            throw new StoreException("Could not list transcript entries", e);
        }
    }

    @Override
    public TranscriptEntry updateSamlAnalysis(
            String entryId, String correlationId, java.util.Map<String, Object> samlSummary) {
        try (var connection = database.open();
             var select = connection.prepareStatement(
                     "SELECT document_json FROM transcript_entries WHERE id = ?")) {
            select.setString(1, entryId);
            TranscriptEntry existing;
            try (var rows = select.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Unknown Transcript entry");
                existing = json.read(rows.getString(1), TranscriptEntry.class);
            }
            var updated = new TranscriptEntry(
                    existing.id(), existing.runId(), existing.direction(), existing.timestamp(),
                    correlationId, existing.method(), existing.url(), existing.status(),
                    existing.headers(), existing.bodyRef(), existing.bodyBytes(), existing.decodedSamlRef(),
                    existing.decodedSamlBytes(), existing.contentType(), existing.rawQuery(),
                    java.util.Map.copyOf(samlSummary == null ? java.util.Map.of() : samlSummary));
            try (var update = connection.prepareStatement(
                    "UPDATE transcript_entries SET document_json = ? WHERE id = ?")) {
                update.setString(1, json.write(updated));
                update.setString(2, entryId);
                update.executeUpdate();
            }
            return updated;
        } catch (SQLException e) {
            throw new StoreException("Could not update Transcript summary", e);
        }
    }

    @Override
    public byte[] readDecodedSaml(TranscriptEntry entry) {
        if (entry == null || entry.decodedSamlRef() == null || entry.decodedSamlRef().isBlank()) {
            throw new IllegalArgumentException("Transcript entry has no decoded SAML content");
        }
        var dataRoot = directory.getParent().toAbsolutePath().normalize();
        var resolved = dataRoot.resolve(entry.decodedSamlRef()).normalize();
        if (!resolved.startsWith(directory.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Transcript content reference escapes the transcript directory");
        }
        try {
            var bytes = Files.readAllBytes(resolved);
            if (bytes.length != entry.decodedSamlBytes()) {
                throw new StoreException("Decoded SAML content length does not match its Transcript entry");
            }
            return bytes;
        } catch (IOException e) {
            throw new StoreException("Could not read decoded SAML content", e);
        }
    }

    private Path safeRunDirectory(String runId) {
        if (runId == null || !runId.matches("run_[0-9A-HJKMNP-TV-Z]{26}")) {
            throw new IllegalArgumentException("Invalid run ID");
        }
        return directory.resolve(runId);
    }

    private String writeIfPresent(Path parent, String name, byte[] bytes) throws IOException {
        if (bytes.length == 0) return null;
        var path = parent.resolve(name);
        Files.write(path, bytes);
        return directory.getParent().relativize(path).toString();
    }
}
