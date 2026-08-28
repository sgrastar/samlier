package org.samlier.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;

public final class SqliteDatabase {
    private final String jdbcUrl;

    public SqliteDatabase(Path dataDirectory) {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            throw new StoreException("Could not create data directory", e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dataDirectory.resolve("samlier.db").toAbsolutePath();
        migrate();
    }

    public Connection open() {
        try {
            var connection = DriverManager.getConnection(jdbcUrl);
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA journal_mode = WAL");
            }
            return connection;
        } catch (SQLException e) {
            throw new StoreException("Could not open SQLite database", e);
        }
    }

    private void migrate() {
        try (var connection = open()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
            }
            var applied = false;
            try (var query = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = 1")) {
                applied = query.executeQuery().next();
            }
            if (!applied) {
                var resource = SqliteDatabase.class.getResourceAsStream("/db/migration/V001__initial_schema.sql");
                if (resource == null) throw new StoreException("Missing database migration V001");
                var sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                try (var statement = connection.createStatement()) {
                    for (var command : sql.split(";")) {
                        if (!command.isBlank()) statement.execute(command);
                    }
                }
                try (var insert = connection.prepareStatement(
                        "INSERT INTO schema_migrations(version, applied_at) VALUES(1, ?)")) {
                    insert.setString(1, Instant.now().toString());
                    insert.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException | IOException e) {
            throw new StoreException("Could not apply database migrations", e);
        }
    }
}
