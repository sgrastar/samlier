package org.samlier.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboxEntry;
import org.samlier.core.caseexec.OutboxStatus;

public final class SqliteCaseExecutionRepository implements CaseExecutionRepository {
    private final SqliteDatabase database;
    private final JsonCodec json;

    public SqliteCaseExecutionRepository(SqliteDatabase database, JsonCodec json) {
        this.database = database;
        this.json = json;
    }

    @Override
    public Optional<CaseExecution> find(String runId, String caseId) {
        try (var connection = database.open();
             var statement = connection.prepareStatement(
                     "SELECT revision, document_json FROM case_executions WHERE run_id = ? AND case_id = ?")) {
            statement.setString(1, runId);
            statement.setString(2, caseId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                var execution = json.read(rows.getString("document_json"), CaseExecution.class);
                if (execution.revision() != rows.getLong("revision")) {
                    throw new StoreException("Case execution revision does not match its document");
                }
                return Optional.of(execution);
            }
        } catch (SQLException e) {
            throw new StoreException("Could not load case execution", e);
        }
    }

    @Override
    public List<CaseExecution> list(String runId) {
        var executions = new ArrayList<CaseExecution>();
        try (var connection = database.open();
             var statement = connection.prepareStatement(
                     "SELECT revision, document_json FROM case_executions WHERE run_id = ? ORDER BY case_id")) {
            statement.setString(1, runId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var execution = json.read(rows.getString("document_json"), CaseExecution.class);
                    if (execution.revision() != rows.getLong("revision")) {
                        throw new StoreException("Case execution revision does not match its document");
                    }
                    executions.add(execution);
                }
            }
            return List.copyOf(executions);
        } catch (SQLException e) {
            throw new StoreException("Could not list case executions", e);
        }
    }

    @Override
    public boolean apply(long expectedRevision, CaseExecution execution, List<OutboundAction> actions) {
        if (expectedRevision < -1) throw new IllegalArgumentException("expectedRevision is invalid");
        if (execution.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("Case execution revision must advance by one");
        }
        try (var connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                if (!writeExecution(connection, expectedRevision, execution)) {
                    connection.rollback();
                    return false;
                }
                for (var action : actions) insertAction(connection, execution, action);
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof StoreException storeException) throw storeException;
                throw new StoreException("Could not persist case transition", e);
            }
        } catch (SQLException e) {
            throw new StoreException("Could not persist case transition", e);
        }
    }

    @Override
    public List<OutboxEntry> listOutbox(String runId) {
        var entries = new ArrayList<OutboxEntry>();
        try (var connection = database.open();
             var statement = connection.prepareStatement(
                     "SELECT * FROM outbox_actions WHERE run_id = ? ORDER BY created_at, action_id")) {
            statement.setString(1, runId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) entries.add(readOutbox(rows));
            }
            return List.copyOf(entries);
        } catch (SQLException e) {
            throw new StoreException("Could not list outbox", e);
        }
    }

    @Override
    public Optional<OutboxEntry> findOutbox(String actionId) {
        try (var connection = database.open();
             var statement = connection.prepareStatement("SELECT * FROM outbox_actions WHERE action_id = ?")) {
            statement.setString(1, actionId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(readOutbox(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("Could not load outbox action", e);
        }
    }

    @Override
    public boolean transitionOutbox(
            String actionId,
            OutboxStatus expected,
            OutboxStatus next,
            Map<String, Object> sendResult,
            String transcriptEntryId,
            Instant updatedAt) {
        if (!expected.canTransitionTo(next)) {
            throw new IllegalArgumentException("Invalid outbox transition: " + expected + " -> " + next);
        }
        var cleanResult = new CaseState("send-result", sendResult == null ? Map.of() : sendResult).data();
        try (var connection = database.open();
             var statement = connection.prepareStatement("""
                     UPDATE outbox_actions
                        SET status = ?, send_result_json = ?, transcript_entry_id = ?, updated_at = ?
                      WHERE action_id = ? AND status = ?
                     """)) {
            statement.setString(1, next.name());
            statement.setString(2, json.write(cleanResult));
            statement.setString(3, transcriptEntryId);
            statement.setString(4, updatedAt.toString());
            statement.setString(5, actionId);
            statement.setString(6, expected.name());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("Could not transition outbox action", e);
        }
    }

    @Override
    public int recoverSendingAsUnknownDelivery(Instant updatedAt) {
        try (var connection = database.open();
             var statement = connection.prepareStatement("""
                     UPDATE outbox_actions SET status = ?, updated_at = ? WHERE status = ?
                     """)) {
            statement.setString(1, OutboxStatus.UNKNOWN_DELIVERY.name());
            statement.setString(2, updatedAt.toString());
            statement.setString(3, OutboxStatus.SENDING.name());
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("Could not recover in-flight outbox actions", e);
        }
    }

    private boolean writeExecution(Connection connection, long expectedRevision, CaseExecution execution)
            throws SQLException {
        var sql = expectedRevision < 0 ? """
                INSERT OR IGNORE INTO case_executions(run_id, case_id, revision, status, document_json, updated_at)
                VALUES(?, ?, ?, ?, ?, ?)
                """ : """
                UPDATE case_executions SET revision = ?, status = ?, document_json = ?, updated_at = ?
                 WHERE run_id = ? AND case_id = ? AND revision = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            if (expectedRevision >= 0) {
                statement.setLong(1, execution.revision());
                statement.setString(2, execution.status().name());
                statement.setString(3, json.write(execution));
                statement.setString(4, execution.updatedAt().toString());
                statement.setString(5, execution.runId());
                statement.setString(6, execution.caseId());
                statement.setLong(7, expectedRevision);
                return statement.executeUpdate() == 1;
            }
            statement.setString(1, execution.runId());
            statement.setString(2, execution.caseId());
            statement.setLong(3, execution.revision());
            statement.setString(4, execution.status().name());
            statement.setString(5, json.write(execution));
            statement.setString(6, execution.updatedAt().toString());
            return statement.executeUpdate() == 1;
        }
    }

    private void insertAction(Connection connection, CaseExecution execution, OutboundAction action) throws SQLException {
        var actionJson = json.write(action);
        try (var statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO outbox_actions(
                    action_id, run_id, case_id, kind, status, action_json, send_result_json,
                    transcript_entry_id, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, '{}', NULL, ?, ?)
                """)) {
            statement.setString(1, action.actionId());
            statement.setString(2, execution.runId());
            statement.setString(3, execution.caseId());
            statement.setString(4, action.kind().name());
            statement.setString(5, OutboxStatus.PENDING.name());
            statement.setString(6, actionJson);
            statement.setString(7, execution.updatedAt().toString());
            statement.setString(8, execution.updatedAt().toString());
            statement.executeUpdate();
        }
        try (var verify = connection.prepareStatement(
                "SELECT run_id, case_id, action_json FROM outbox_actions WHERE action_id = ?")) {
            verify.setString(1, action.actionId());
            try (var rows = verify.executeQuery()) {
                if (!rows.next()
                        || !execution.runId().equals(rows.getString(1))
                        || !execution.caseId().equals(rows.getString(2))
                        || !actionJson.equals(rows.getString(3))) {
                    throw new StoreException("actionId collision with different outbox content");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private OutboxEntry readOutbox(java.sql.ResultSet rows) throws SQLException {
        return new OutboxEntry(
                rows.getString("run_id"),
                rows.getString("case_id"),
                json.read(rows.getString("action_json"), OutboundAction.class),
                OutboxStatus.valueOf(rows.getString("status")),
                json.read(rows.getString("send_result_json"), Map.class),
                rows.getString("transcript_entry_id"),
                Instant.parse(rows.getString("created_at")),
                Instant.parse(rows.getString("updated_at")));
    }
}
