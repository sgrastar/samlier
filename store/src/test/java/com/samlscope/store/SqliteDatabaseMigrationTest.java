package com.samlscope.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteDatabaseMigrationTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void upgradesAnExistingV1DatabaseWithoutRecreatingIt() throws Exception {
        Files.createDirectories(directory);
        var url = "jdbc:sqlite:" + directory.resolve("samlscope.db").toAbsolutePath();
        try (var connection = DriverManager.getConnection(url)) {
            var resource = SqliteDatabase.class.getResourceAsStream("/db/migration/V001__initial_schema.sql");
            var sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            try (var statement = connection.createStatement()) {
                for (var command : sql.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
                statement.execute("INSERT INTO schema_migrations(version, applied_at) VALUES(1, '2026-08-29T00:00:00Z')");
            }
        }

        var upgraded = new SqliteDatabase(directory);

        try (var connection = upgraded.open();
             var versions = connection.createStatement().executeQuery(
                     "SELECT version FROM schema_migrations ORDER BY version")) {
            assertTrue(versions.next());
            assertEquals(1, versions.getInt(1));
            assertTrue(versions.next());
            assertEquals(2, versions.getInt(1));
            assertTrue(versions.next());
            assertEquals(3, versions.getInt(1));
            assertTrue(versions.next());
            assertEquals(4, versions.getInt(1));
        }
        try (var connection = upgraded.open();
             var table = connection.createStatement().executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='outbox_actions'")) {
            assertTrue(table.next());
        }
        try (var connection = upgraded.open();
             var table = connection.createStatement().executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='applicability_inputs'")) {
            assertTrue(table.next());
        }
        try (var connection = upgraded.open();
             var table = connection.createStatement().executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='run_access_grants'")) {
            assertTrue(table.next());
        }
    }

    @Test
    void continuesUsingLegacyDatabaseWhenRenamedDatabaseDoesNotExist() throws Exception {
        var legacyDatabase = directory.resolve("samlier.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + legacyDatabase.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE legacy_marker (value TEXT NOT NULL)");
            statement.execute("INSERT INTO legacy_marker(value) VALUES ('preserved')");
        }

        var database = new SqliteDatabase(directory);

        try (var connection = database.open();
             var marker = connection.createStatement().executeQuery("SELECT value FROM legacy_marker")) {
            assertTrue(marker.next());
            assertEquals("preserved", marker.getString(1));
        }
        assertTrue(Files.exists(legacyDatabase));
        assertTrue(Files.notExists(directory.resolve("samlscope.db")));
    }

    @Test
    void transcriptUsageMigrationBackfillsExistingEvidence() throws Exception {
        Files.createDirectories(directory);
        var url = "jdbc:sqlite:" + directory.resolve("samlscope.db").toAbsolutePath();
        var migrations = new String[] {
                "/db/migration/V001__initial_schema.sql",
                "/db/migration/V002__case_execution_outbox.sql",
                "/db/migration/V003__applicability_inputs.sql",
                "/db/migration/V004__run_access_grants.sql",
                "/db/migration/V005__published_runs.sql",
                "/db/migration/V006__run_session_lookup.sql"
        };
        try (var connection = DriverManager.getConnection(url)) {
            for (var index = 0; index < migrations.length; index++) {
                var resource = SqliteDatabase.class.getResourceAsStream(migrations[index]);
                var sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                try (var statement = connection.createStatement()) {
                    for (var command : sql.split(";")) {
                        if (!command.isBlank()) statement.execute(command);
                    }
                    statement.execute("INSERT INTO schema_migrations(version, applied_at) VALUES("
                            + (index + 1) + ", '2026-08-29T00:00:00Z')");
                }
            }
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO plans(id, document_json, created_at, updated_at) VALUES("
                        + "'plan_0123456789ABCDEFGHJKMNPQRS', '{}', '2026-08-29T00:00:00Z', "
                        + "'2026-08-29T00:00:00Z')");
                statement.execute("INSERT INTO runs(id, plan_id, status, document_json, created_at, updated_at) VALUES("
                        + "'run_0123456789ABCDEFGHJKMNPQRS', 'plan_0123456789ABCDEFGHJKMNPQRS', "
                        + "'RUNNING', '{}', '2026-08-29T00:00:00Z', '2026-08-29T00:00:00Z')");
                statement.execute("INSERT INTO transcript_entries(id, run_id, timestamp, document_json) VALUES("
                        + "'tx_0123456789ABCDEFGHJKMNPQRS', 'run_0123456789ABCDEFGHJKMNPQRS', "
                        + "'2026-08-29T00:00:00Z', '{\"bodyBytes\":7,\"decodedSamlBytes\":11}')");
            }
        }

        var upgraded = new SqliteDatabase(directory);

        try (var connection = upgraded.open();
             var usage = connection.createStatement().executeQuery(
                     "SELECT entry_count, stored_bytes, rejected FROM transcript_usage")) {
            assertTrue(usage.next());
            assertEquals(1, usage.getInt(1));
            assertEquals(18, usage.getLong(2));
            assertEquals(0, usage.getInt(3));
        }
        try (var connection = upgraded.open();
             var usage = connection.createStatement().executeQuery(
                     "SELECT entry_count, stored_bytes FROM transcript_global_usage")) {
            assertTrue(usage.next());
            assertEquals(1, usage.getLong(1));
            assertEquals(18, usage.getLong(2));
        }
    }
}
