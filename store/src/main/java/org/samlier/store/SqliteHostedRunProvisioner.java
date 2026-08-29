package org.samlier.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
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

    public boolean createRun(TestRun run, RunAccessGrant grant) {
        return provision(null, run, grant, false);
    }

    /**
     * Updates a Plan atomically with Run creation. A target entity ID cannot change while
     * that Plan has a non-terminal Run; other Plan edits remain allowed.
     */
    public boolean updatePlanUnlessActiveRetarget(TestPlan updated) {
        try (var connection = database.open()) {
            execute(connection, "BEGIN IMMEDIATE");
            try {
                var existing = findPlan(connection, updated.id())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown Test Plan"));
                var changesTarget = !existing.target().entityId().equals(updated.target().entityId());
                if (changesTarget && hasActiveRunForPlan(connection, updated.id())) {
                    execute(connection, "ROLLBACK");
                    return false;
                }
                updatePlan(connection, updated);
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
            throw new StoreException("Could not update Hosted Plan", error);
        }
    }

    private boolean provision(TestPlan plan, TestRun run, RunAccessGrant grant, boolean insertPlan) {
        if ((insertPlan && (plan == null || !run.planId().equals(plan.id())))
                || !grant.runId().equals(run.id())) {
            throw new IllegalArgumentException("Provisioning records do not belong together");
        }
        try (var connection = database.open()) {
            execute(connection, "BEGIN IMMEDIATE");
            try {
                var currentPlan = insertPlan ? plan : findPlan(connection, run.planId())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown Test Plan"));
                if (hasActiveRunForTarget(connection, currentPlan.target().entityId())) {
                    execute(connection, "ROLLBACK");
                    return false;
                }
                if (insertPlan) insertPlan(connection, currentPlan);
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

    private Optional<TestPlan> findPlan(Connection connection, String id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT document_json FROM plans WHERE id = ?")) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) {
                return rows.next()
                        ? Optional.of(json.read(rows.getString(1), TestPlan.class))
                        : Optional.empty();
            }
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

    private boolean hasActiveRunForPlan(Connection connection, String planId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM runs
                WHERE plan_id = ? AND status NOT IN ('COMPLETED', 'ABORTED')
                LIMIT 1
                """)) {
            statement.setString(1, planId);
            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
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

    private void updatePlan(Connection connection, TestPlan plan) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE plans SET document_json = ?, updated_at = ? WHERE id = ?")) {
            statement.setString(1, json.write(plan));
            statement.setString(2, plan.updatedAt().toString());
            statement.setString(3, plan.id());
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Unknown Test Plan");
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
