package org.samlier.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.samlier.core.access.RunAccessGrant;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.TestRun;

/** Atomically enforces the Hosted active-target limit while storing a Run and its access grant. */
public final class SqliteHostedRunProvisioner {
    private final SqliteDatabase database;
    private final JsonCodec json;

    public SqliteHostedRunProvisioner(SqliteDatabase database, JsonCodec json) {
        this.database = Objects.requireNonNull(database, "database");
        this.json = Objects.requireNonNull(json, "json");
    }

    public boolean createPlanWithInitialRun(TestPlan plan, TestRun run, RunAccessGrant grant) {
        return provision(plan, run, grant, true);
    }

    public boolean createRun(TestPlan plan, TestRun run, RunAccessGrant grant) {
        return provision(plan, run, grant, false);
    }

    private boolean provision(TestPlan plan, TestRun run, RunAccessGrant grant, boolean insertPlan) {
        if (!run.planId().equals(plan.id()) || !grant.runId().equals(run.id())) {
            throw new IllegalArgumentException("Provisioning records do not belong together");
        }
        try (var connection = database.open()) {
            execute(connection, "BEGIN IMMEDIATE");
            try {
                if (hasActiveRunForTarget(connection, plan.target().entityId())) {
                    execute(connection, "ROLLBACK");
                    return false;
                }
                if (insertPlan) insertPlan(connection, plan);
                insertRun(connection, run);
                insertGrant(connection, grant);
                execute(connection, "COMMIT");
                return true;
            } catch (SQLException error) {
                rollback(connection, error);
                throw error;
            } catch (RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (SQLException error) {
            throw new StoreException("Could not provision Hosted Run", error);
        }
    }

    private boolean hasActiveRunForTarget(Connection connection, String entityId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT p.document_json
                FROM runs r JOIN plans p ON p.id = r.plan_id
                WHERE r.status NOT IN ('COMPLETED', 'ABORTED')
                """); var rows = statement.executeQuery()) {
            while (rows.next()) {
                var existing = json.read(rows.getString(1), TestPlan.class);
                if (existing.target().entityId().equals(entityId)) return true;
            }
            return false;
        }
    }

    private void insertPlan(Connection connection, TestPlan plan) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO plans(id, document_json, created_at, updated_at) VALUES(?, ?, ?, ?)")) {
            statement.setString(1, plan.id());
            statement.setString(2, json.write(plan));
            statement.setString(3, plan.createdAt().toString());
            statement.setString(4, plan.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    private void insertRun(Connection connection, TestRun run) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO runs(id, plan_id, status, document_json, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, run.id());
            statement.setString(2, run.planId());
            statement.setString(3, run.status().name());
            statement.setString(4, json.write(run));
            statement.setString(5, run.createdAt().toString());
            statement.setString(6, run.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    private void insertGrant(Connection connection, RunAccessGrant grant) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO run_access_grants(
                    run_id, access_token_hash, session_token_hash, csrf_token_hash, updated_at, revoked
                ) VALUES(?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, grant.runId());
            statement.setString(2, grant.accessTokenHash());
            statement.setString(3, grant.sessionTokenHash());
            statement.setString(4, grant.csrfTokenHash());
            statement.setString(5, grant.updatedAt().toString());
            statement.setBoolean(6, grant.revoked());
            statement.executeUpdate();
        }
    }

    private void rollback(Connection connection, Exception original) {
        try {
            execute(connection, "ROLLBACK");
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private void execute(Connection connection, String command) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(command);
        }
    }
}
