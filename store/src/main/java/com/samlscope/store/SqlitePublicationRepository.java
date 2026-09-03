package com.samlscope.store;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

/** Hosted publication state. Self-hosted export does not use this table. */
public final class SqlitePublicationRepository {
    private final SqliteDatabase database;

    public SqlitePublicationRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public boolean publish(String runId, Instant publishedAt) {
        validateRunId(runId);
        Objects.requireNonNull(publishedAt, "publishedAt");
        try (var connection = database.open();
             var statement = connection.prepareStatement(
                     "INSERT INTO published_runs(run_id, published_at) "
                             + "SELECT ?, ? WHERE NOT EXISTS ("
                             + "SELECT 1 FROM transcript_usage WHERE run_id = ? AND rejected = 1) "
                             + "ON CONFLICT(run_id) DO UPDATE SET published_at = excluded.published_at")) {
            statement.setString(1, runId);
            statement.setString(2, publishedAt.toString());
            statement.setString(3, runId);
            return statement.executeUpdate() == 1;
        } catch (SQLException error) {
            throw new StoreException("Could not publish Run", error);
        }
    }

    public boolean isPublished(String runId) {
        validateRunId(runId);
        try (var connection = database.open();
             var statement = connection.prepareStatement("SELECT 1 FROM published_runs WHERE run_id = ?")) {
            statement.setString(1, runId);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        } catch (SQLException error) {
            throw new StoreException("Could not read Run publication state", error);
        }
    }

    public boolean isTranscriptEvidenceComplete(String runId) {
        validateRunId(runId);
        try (var connection = database.open();
             var statement = connection.prepareStatement(
                     "SELECT 1 FROM transcript_usage WHERE run_id = ? AND rejected = 1")) {
            statement.setString(1, runId);
            try (var rows = statement.executeQuery()) {
                return !rows.next();
            }
        } catch (SQLException error) {
            throw new StoreException("Could not read Transcript evidence state", error);
        }
    }

    private void validateRunId(String runId) {
        if (runId == null || !runId.matches("run_[0-9A-HJKMNP-TV-Z]{26}")) {
            throw new IllegalArgumentException("Invalid run ID");
        }
    }
}
