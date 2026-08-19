package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Task63PersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static AutoCloseable application;

    @BeforeAll
    static void startAnalyticsWithPostgres() {
        application = Task63TestSupport.startAnalyticsApplication(
                "server.port=0",
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate");
    }

    @AfterAll
    static void stopAnalytics() throws Exception {
        if (application != null) {
            application.close();
        }
    }

    @BeforeEach
    void requireTask63Schema() throws SQLException {
        Task63TestSupport.assertRequiredMigrationTablesExist();
        try (var connection = connection()) {
            assertThat(tableExists(connection, "analytics_bets")).isTrue();
            assertThat(tableExists(connection, "processed_events")).isTrue();
        }
    }

    @Test
    void should_persist_contractual_tables_with_unique_identity_and_decimal_columns() throws SQLException {
        try (var connection = connection()) {
            assertThat(uniqueIndexContains(connection, "analytics_bets", "bet_id")).isTrue();
            assertThat(uniqueIndexContains(connection, "processed_events", "event_id")).isTrue();
            assertThat(columnType(connection, "analytics_bets", "odds")).isIn("numeric", "decimal");
            assertThat(columnType(connection, "analytics_bets", "stake")).isIn("numeric", "decimal");
            assertThat(columnType(connection, "analytics_bets", "profit")).isIn("numeric", "decimal");
            assertThat(columnType(connection, "analytics_bets", "return_amount")).isIn("numeric", "decimal");
        }
    }

    @Test
    void should_enforce_durable_processed_event_id_uniqueness_in_postgresql() throws SQLException {
        var eventId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        try (var connection = connection()) {
            var statement = connection.prepareStatement(
                    "insert into processed_events(event_id, event_type, processed_at) values (?, ?, ?)");
            statement.setObject(1, eventId);
            statement.setString(2, "BET_CREATED");
            statement.setObject(3, Task63TestSupport.FIXED_PROCESSED_AT.atOffset(ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException firstInsertFailure) {
            throw new AssertionError("processed_events must accept its documented required columns", firstInsertFailure);
        }

        try (var connection = connection()) {
            var statement = connection.prepareStatement(
                    "insert into processed_events(event_id, event_type, processed_at) values (?, ?, ?)");
            statement.setObject(1, eventId);
            statement.setString(2, "BET_CREATED");
            statement.setObject(3, Task63TestSupport.FIXED_PROCESSED_AT.atOffset(ZoneOffset.UTC));
            assertThatThrownBy(statement::executeUpdate)
                    .isInstanceOf(SQLException.class);
        }
    }

    private static java.sql.Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static boolean tableExists(java.sql.Connection connection, String table) throws SQLException {
        var statement = connection.prepareStatement(
                "select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = ?)");
        statement.setString(1, table);
        try (var result = statement.executeQuery()) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private static boolean uniqueIndexContains(java.sql.Connection connection, String table, String column)
            throws SQLException {
        var statement = connection.prepareStatement(
                "select exists (select 1 from pg_indexes where schemaname = 'public' and tablename = ? "
                        + "and indexdef ilike '%UNIQUE%' and indexdef ilike ?)");
        statement.setString(1, table);
        statement.setString(2, "%" + column + "%");
        try (var result = statement.executeQuery()) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private static String columnType(java.sql.Connection connection, String table, String column) throws SQLException {
        var statement = connection.prepareStatement(
                "select data_type from information_schema.columns where table_schema = 'public' "
                        + "and table_name = ? and column_name = ?");
        statement.setString(1, table);
        statement.setString(2, column);
        try (var result = statement.executeQuery()) {
            assertThat(result.next()).as("column %s.%s must exist", table, column).isTrue();
            return result.getString(1);
        }
    }
}
