package com.samlscope.store;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.samlscope.core.run.RunRepository;
import com.samlscope.core.run.TestRun;

public final class SqliteRunRepository implements RunRepository {
    private final SqliteDatabase database;
    private final JsonCodec json;

    public SqliteRunRepository(SqliteDatabase database, JsonCodec json) {
        this.database = database;
        this.json = json;
    }

    @Override
    public List<TestRun> listForPlan(String planId) {
        var runs = new ArrayList<TestRun>();
        try (var connection = database.open();
             var statement = connection.prepareStatement(
                     "SELECT document_json FROM runs WHERE plan_id = ? ORDER BY created_at DESC")) {
            statement.setString(1, planId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) runs.add(json.read(rows.getString(1), TestRun.class));
            }
            return List.copyOf(runs);
        } catch (SQLException e) {
            throw new StoreException("Could not list runs", e);
        }
    }

    @Override
    public Optional<TestRun> find(String id) {
        try (var connection = database.open();
             var statement = connection.prepareStatement("SELECT document_json FROM runs WHERE id = ?")) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(json.read(rows.getString(1), TestRun.class)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("Could not load run", e);
        }
    }

    @Override
    public void save(TestRun run) {
        var sql = """
                INSERT INTO runs(id, plan_id, status, document_json, created_at, updated_at) VALUES(?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET status=excluded.status, document_json=excluded.document_json, updated_at=excluded.updated_at
                """;
        try (var connection = database.open(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, run.id());
            statement.setString(2, run.planId());
            statement.setString(3, run.status().name());
            statement.setString(4, json.write(run));
            statement.setString(5, run.createdAt().toString());
            statement.setString(6, run.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("Could not save run", e);
        }
    }
}
