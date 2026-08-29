package org.samlier.store;

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
        var url = "jdbc:sqlite:" + directory.resolve("samlier.db").toAbsolutePath();
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
        }
        try (var connection = upgraded.open();
             var table = connection.createStatement().executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='outbox_actions'")) {
            assertTrue(table.next());
        }
    }
}
