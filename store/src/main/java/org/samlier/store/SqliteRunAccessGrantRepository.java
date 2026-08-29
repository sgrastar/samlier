package org.samlier.store;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.samlier.core.access.RunAccessGrant;
import org.samlier.core.access.RunAccessGrantRepository;

public final class SqliteRunAccessGrantRepository implements RunAccessGrantRepository {
    private final SqliteDatabase database;

    public SqliteRunAccessGrantRepository(SqliteDatabase database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override
    public Optional<RunAccessGrant> find(String runId) {
        try (var connection = database.open();
             var statement = connection.prepareStatement("""
                     SELECT run_id, access_token_hash, session_token_hash, csrf_token_hash, updated_at, revoked
                     FROM run_access_grants WHERE run_id = ?
                     """)) {
            statement.setString(1, runId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new RunAccessGrant(
                        rows.getString("run_id"), rows.getString("access_token_hash"),
                        rows.getString("session_token_hash"), rows.getString("csrf_token_hash"),
                        Instant.parse(rows.getString("updated_at")), rows.getBoolean("revoked")));
            }
        } catch (SQLException error) {
            throw new StoreException("Could not read Run access grant", error);
        }
    }

    @Override
    public Optional<RunAccessGrant> findBySessionTokenHash(String sessionTokenHash) {
        try (var connection = database.open();
             var statement = connection.prepareStatement("""
                     SELECT run_id, access_token_hash, session_token_hash, csrf_token_hash, updated_at, revoked
                     FROM run_access_grants WHERE session_token_hash = ?
                     """)) {
            statement.setString(1, sessionTokenHash);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new RunAccessGrant(
                        rows.getString("run_id"), rows.getString("access_token_hash"),
                        rows.getString("session_token_hash"), rows.getString("csrf_token_hash"),
                        Instant.parse(rows.getString("updated_at")), rows.getBoolean("revoked")));
            }
        } catch (SQLException error) {
            throw new StoreException("Could not find Run access grant by session", error);
        }
    }

    @Override
    public void save(RunAccessGrant grant) {
        try (var connection = database.open();
             var statement = connection.prepareStatement("""
                     INSERT INTO run_access_grants(
                         run_id, access_token_hash, session_token_hash, csrf_token_hash, updated_at, revoked
                     ) VALUES(?, ?, ?, ?, ?, ?)
                     ON CONFLICT(run_id) DO UPDATE SET
                         access_token_hash = excluded.access_token_hash,
                         session_token_hash = excluded.session_token_hash,
                         csrf_token_hash = excluded.csrf_token_hash,
                         updated_at = excluded.updated_at,
                         revoked = excluded.revoked
                     """)) {
            statement.setString(1, grant.runId());
            statement.setString(2, grant.accessTokenHash());
            statement.setString(3, grant.sessionTokenHash());
            statement.setString(4, grant.csrfTokenHash());
            statement.setString(5, grant.updatedAt().toString());
            statement.setBoolean(6, grant.revoked());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new StoreException("Could not store Run access grant", error);
        }
    }

    @Override
    public boolean delete(String runId) {
        try (var connection = database.open();
             var statement = connection.prepareStatement("DELETE FROM run_access_grants WHERE run_id = ?")) {
            statement.setString(1, runId);
            return statement.executeUpdate() > 0;
        } catch (SQLException error) {
            throw new StoreException("Could not delete Run access grant", error);
        }
    }
}
