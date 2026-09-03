package com.samlscope.store;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import com.samlscope.core.evaluation.ApplicabilityInput;
import com.samlscope.core.evaluation.ApplicabilityInputRepository;

public final class SqliteApplicabilityInputRepository implements ApplicabilityInputRepository {
    private final SqliteDatabase database;
    private final JsonCodec json;

    public SqliteApplicabilityInputRepository(SqliteDatabase database, JsonCodec json) {
        this.database = java.util.Objects.requireNonNull(database, "database");
        this.json = java.util.Objects.requireNonNull(json, "json");
    }

    @Override
    public Optional<ApplicabilityInput> find(String runId, String predicate) {
        requireText(runId, "runId");
        requireText(predicate, "predicate");
        try (var connection = database.open();
             var statement = connection.prepareStatement(
                     "SELECT document_json FROM applicability_inputs WHERE run_id = ? AND predicate = ?")) {
            statement.setString(1, runId);
            statement.setString(2, predicate);
            try (var rows = statement.executeQuery()) {
                return rows.next()
                        ? Optional.of(json.read(rows.getString(1), ApplicabilityInput.class))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("Could not load applicability input", e);
        }
    }

    @Override
    public void save(String runId, String predicate, ApplicabilityInput input, Instant updatedAt) {
        requireText(runId, "runId");
        requireText(predicate, "predicate");
        java.util.Objects.requireNonNull(input, "input");
        java.util.Objects.requireNonNull(updatedAt, "updatedAt");
        var sql = """
                INSERT INTO applicability_inputs(run_id, predicate, document_json, updated_at) VALUES(?, ?, ?, ?)
                ON CONFLICT(run_id, predicate) DO UPDATE SET
                    document_json=excluded.document_json, updated_at=excluded.updated_at
                """;
        try (var connection = database.open(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            statement.setString(2, predicate);
            statement.setString(3, json.write(input));
            statement.setString(4, updatedAt.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("Could not save applicability input", e);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
