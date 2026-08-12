package com.suaposta.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

final class AuthRegistrationDatabaseAssertions {

    private static final String TEXT_COLUMNS_SQL = """
            SELECT table_schema, table_name, column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND data_type IN ('character varying', 'character', 'text', 'json', 'jsonb', 'xml')
            ORDER BY table_schema, table_name, ordinal_position
            """;

    private static final String BASE_TABLES_SQL = """
            SELECT table_schema, table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_type = 'BASE TABLE'
            ORDER BY table_schema, table_name
            """;

    private AuthRegistrationDatabaseAssertions() {
    }

    static long countRowsInBaseTables() throws SQLException {
        try (var connection = openConnection();
                var tables = connection.prepareStatement(BASE_TABLES_SQL);
                var result = tables.executeQuery()) {
            long count = 0;
            while (result.next()) {
                count += countRows(connection, result.getString(1), result.getString(2));
            }
            return count;
        }
    }

    static long countExactText(String expected) throws SQLException {
        long count = 0;
        try (var connection = openConnection()) {
            for (var column : textColumns(connection)) {
                var sql = "SELECT COUNT(*) FROM " + column.qualifiedTable()
                        + " WHERE CAST(" + quote(column.columnName()) + " AS text) = ?";
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setString(1, expected);
                    try (var result = statement.executeQuery()) {
                        result.next();
                        count += result.getLong(1);
                    }
                }
            }
        }
        return count;
    }

    static boolean containsText(String expected) throws SQLException {
        try (var connection = openConnection()) {
            for (var column : textColumns(connection)) {
                var sql = "SELECT 1 FROM " + column.qualifiedTable()
                        + " WHERE CAST(" + quote(column.columnName()) + " AS text) LIKE ? LIMIT 1";
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setString(1, "%" + expected + "%");
                    try (var result = statement.executeQuery()) {
                        if (result.next()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static Optional<String> findBcryptHashMatching(String rawPassword) throws SQLException {
        var encoder = new BCryptPasswordEncoder();
        try (var connection = openConnection()) {
            for (var column : textColumns(connection)) {
                var sql = "SELECT CAST(" + quote(column.columnName()) + " AS text) FROM "
                        + column.qualifiedTable() + " WHERE " + quote(column.columnName()) + " IS NOT NULL";
                try (var statement = connection.prepareStatement(sql);
                        var result = statement.executeQuery()) {
                    while (result.next()) {
                        var candidate = result.getString(1);
                        try {
                            if (encoder.matches(rawPassword, candidate)) {
                                return Optional.of(candidate);
                            }
                        } catch (IllegalArgumentException ignored) {
                            // Non-BCrypt text values are expected in other columns.
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static long countRows(Connection connection, String schema, String table) throws SQLException {
        var sql = "SELECT COUNT(*) FROM " + quote(schema) + "." + quote(table);
        try (var statement = connection.prepareStatement(sql);
                var result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private static List<TextColumn> textColumns(Connection connection) throws SQLException {
        var columns = new ArrayList<TextColumn>();
        try (var statement = connection.prepareStatement(TEXT_COLUMNS_SQL);
                var result = statement.executeQuery()) {
            while (result.next()) {
                columns.add(new TextColumn(result.getString(1), result.getString(2), result.getString(3)));
            }
        }
        return columns;
    }

    private static Connection openConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("PostgreSQL JDBC test driver is required for persistence tests", exception);
        }
        return DriverManager.getConnection(
                databaseUrl(),
                databaseUser(),
                databasePassword());
    }

    private static String databaseUrl() {
        return setting(
                "auth.test.db.url",
                "AUTH_DB_JDBC_URL",
                "jdbc:postgresql://"
                        + setting("auth.test.db.host", "POSTGRES_HOST", "127.0.0.1")
                        + ":"
                        + setting("auth.test.db.port", "POSTGRES_HOST_PORT", "5432")
                        + "/"
                        + setting("auth.test.db.name", "AUTH_DB_NAME", "suaposta_auth"));
    }

    private static String databaseUser() {
        return setting("auth.test.db.user", "AUTH_DB_USER", "suaposta_auth");
    }

    private static String databasePassword() {
        return setting("auth.test.db.password", "AUTH_DB_PASSWORD", "change_me_auth");
    }

    private static String setting(String property, String environmentVariable, String fallback) {
        var propertyValue = System.getProperty(property);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        var environmentValue = System.getenv(environmentVariable);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        return fallback;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record TextColumn(String schemaName, String tableName, String columnName) {

        private String qualifiedTable() {
            return quote(schemaName) + "." + quote(tableName);
        }
    }
}
