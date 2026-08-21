package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

final class Task71TestSupport {

    static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();
    static final Set<String> SUMMARY_FIELDS = Set.of(
            "totalStake", "totalProfit", "roi", "yield", "winRate", "averageOdds",
            "betsCount", "wonBets", "lostBets", "voidBets", "cashoutBets", "cancelledBets",
            "maxDrawdown", "currentDrawdown");
    static final Set<String> FILTER_FIELDS = Set.of(
            "startDate", "endDate", "sport", "league", "team", "market", "status",
            "minOdds", "maxOdds", "minStake", "maxStake");

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private Task71TestSupport() {
    }

    static ConfigurableApplicationContext startApplication(PostgreSQLContainer<?> postgres) {
        return new SpringApplicationBuilder(AnalyticsServiceApplication.class)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=" + postgres.getJdbcUrl(),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(),
                        "spring.rabbitmq.listener.simple.auto-startup=false")
                .run();
    }

    static void resetDatabase(PostgreSQLContainer<?> postgres) throws SQLException {
        try (var connection = connection(postgres); var statement = connection.createStatement()) {
            statement.executeUpdate("delete from processed_events");
            statement.executeUpdate("delete from analytics_bets");
        }
    }

    static void insertBet(PostgreSQLContainer<?> postgres, BetRow row) throws SQLException {
        try (var connection = connection(postgres);
                var statement = connection.prepareStatement("""
                        insert into analytics_bets(
                            id, bet_id, user_id, sport, league, home_team, away_team, market, selection,
                            odds, stake, status, profit, return_amount, placed_at, settled_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            statement.setObject(1, row.betId());
            statement.setObject(2, row.betId());
            statement.setObject(3, row.userId());
            statement.setString(4, row.sport());
            statement.setString(5, row.league());
            statement.setString(6, row.homeTeam());
            statement.setString(7, row.awayTeam());
            statement.setString(8, row.market());
            statement.setString(9, row.selection());
            statement.setBigDecimal(10, row.odds());
            statement.setBigDecimal(11, row.stake());
            statement.setString(12, row.status());
            statement.setBigDecimal(13, row.profit());
            statement.setBigDecimal(14, row.returnAmount());
            setInstant(statement, 15, row.placedAt());
            setInstant(statement, 16, row.settledAt());
            setInstant(statement, 17, row.createdAt());
            setInstant(statement, 18, row.updatedAt());
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    static void insertProcessedEvent(PostgreSQLContainer<?> postgres, UUID eventId) throws SQLException {
        try (var connection = connection(postgres);
                var statement = connection.prepareStatement(
                        "insert into processed_events(event_id, event_type, processed_at) values (?, ?, ?)")) {
            statement.setObject(1, eventId);
            statement.setString(2, "BET_CREATED");
            statement.setObject(3, OffsetDateTime.ofInstant(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    static long count(PostgreSQLContainer<?> postgres, String table) throws SQLException {
        assertThat(table).isIn("analytics_bets", "processed_events");
        try (var connection = connection(postgres);
                var statement = connection.createStatement();
                var result = statement.executeQuery("select count(*) from " + table)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    static List<String> projectionState(PostgreSQLContainer<?> postgres) throws SQLException {
        try (var connection = connection(postgres);
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        select bet_id, user_id, odds, stake, status, profit, return_amount,
                               placed_at, settled_at, created_at, updated_at
                        from analytics_bets order by bet_id
                        """)) {
            var rows = new java.util.ArrayList<String>();
            while (result.next()) {
                rows.add(List.of(
                        result.getString("bet_id"), result.getString("user_id"),
                        result.getBigDecimal("odds").toPlainString(), result.getBigDecimal("stake").toPlainString(),
                        result.getString("status"), String.valueOf(result.getBigDecimal("profit")),
                        String.valueOf(result.getBigDecimal("return_amount")),
                        String.valueOf(result.getObject("placed_at")), String.valueOf(result.getObject("settled_at")),
                        String.valueOf(result.getObject("created_at")), String.valueOf(result.getObject("updated_at")))
                        .toString());
            }
            return rows;
        }
    }

    static List<String> processedEventState(PostgreSQLContainer<?> postgres) throws SQLException {
        try (var connection = connection(postgres);
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "select event_id, event_type, processed_at from processed_events order by event_id")) {
            var rows = new java.util.ArrayList<String>();
            while (result.next()) {
                rows.add(List.of(
                        result.getString("event_id"), result.getString("event_type"),
                        String.valueOf(result.getObject("processed_at"))).toString());
            }
            return rows;
        }
    }

    static HttpResponse<String> dashboard(
            ConfigurableApplicationContext context, String userId, String query) throws IOException, InterruptedException {
        var port = ((WebServerApplicationContext) context).getWebServer().getPort();
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/analytics/dashboard" + query))
                .GET();
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode body(HttpResponse<String> response) throws IOException {
        return JSON.readTree(response.body());
    }

    static void assertMetric(JsonNode summary, String field, String expected, int scale) {
        assertThat(summary.path(field).isNumber()).as(field + " must be numeric").isTrue();
        assertThat(summary.path(field).decimalValue()).isEqualByComparingTo(expected);
        assertThat(summary.path(field).decimalValue().scale()).as(field + " scale").isEqualTo(scale);
    }

    static void assertCount(JsonNode summary, String field, int expected) {
        assertThat(summary.path(field).isIntegralNumber()).as(field + " must be an integer").isTrue();
        assertThat(summary.path(field).intValue()).isEqualTo(expected);
    }

    static void assertExactFields(JsonNode object, Set<String> expected) {
        assertThat(object.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(expected);
    }

    static void assertSafeError(HttpResponse<String> response, int status) throws IOException {
        assertThat(response.statusCode()).isEqualTo(status);
        var error = body(response);
        assertThat(error.path("status").intValue()).isEqualTo(status);
        assertThat(error.path("timestamp").isTextual()).isTrue();
        assertThat(error.path("error").isTextual()).isTrue();
        assertThat(error.path("message").isTextual()).isTrue();
        assertThat(error.path("path").asText()).isEqualTo("/analytics/dashboard");
        assertThat(response.body()).doesNotContain(
                "JdbcTemplate", "SQLException", "analytics_bets", "processed_events", "stackTrace");
    }

    static void insertMixedFixture(PostgreSQLContainer<?> postgres) throws SQLException {
        insertBet(postgres, row("10000000-0000-0000-0000-000000000001", USER_A, "WON", "100.00", "2.1000",
                "110.00", "210.00", "2026-07-01T10:00:00Z", "2026-07-10T10:00:00Z"));
        insertBet(postgres, row("10000000-0000-0000-0000-000000000002", USER_A, "LOST", "50.00", "2.0000",
                "-50.00", "0.00", "2026-07-02T10:00:00Z", "2026-07-10T11:00:00Z"));
        insertBet(postgres, row("10000000-0000-0000-0000-000000000003", USER_A, "CASHOUT", "100.00", "1.8000",
                "-20.00", "80.13", "2026-07-03T10:00:00Z", "2026-07-10T12:00:00Z"));
        insertBet(postgres, row("10000000-0000-0000-0000-000000000004", USER_A, "VOID", "70.00", "3.5000",
                "0.00", "70.00", "2026-07-04T10:00:00Z", "2026-07-10T13:00:00Z"));
        insertBet(postgres, row("10000000-0000-0000-0000-000000000005", USER_A, "CANCELLED", "80.00", "4.5000",
                "0.00", "80.00", "2026-07-05T10:00:00Z", "2026-07-10T14:00:00Z"));
        insertBet(postgres, row("10000000-0000-0000-0000-000000000006", USER_A, "PENDING", "90.00", "5.5000",
                null, null, "2026-07-06T10:00:00Z", null));
        insertBet(postgres, row("20000000-0000-0000-0000-000000000001", USER_B, "WON", "999999.99", "9.9999",
                "888888.88", "999999.99", "2026-07-01T10:00:00Z", "2026-07-10T10:00:00Z"));
    }

    static BetRow row(
            String betId, UUID userId, String status, String stake, String odds, String profit,
            String returnAmount, String placedAt, String settledAt) {
        var id = UUID.fromString(betId);
        var placed = Instant.parse(placedAt);
        return new BetRow(
                id, userId, "FOOTBALL", "Premier League", "Home FC", "Away FC", "MATCH_RESULT", "Home FC",
                new BigDecimal(odds), new BigDecimal(stake), status,
                profit == null ? null : new BigDecimal(profit),
                returnAmount == null ? null : new BigDecimal(returnAmount),
                placed, settledAt == null ? null : Instant.parse(settledAt),
                placed.plusSeconds(60), placed.plusSeconds(120));
    }

    private static Connection connection(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setObject(index, value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    record BetRow(
            UUID betId,
            UUID userId,
            String sport,
            String league,
            String homeTeam,
            String awayTeam,
            String market,
            String selection,
            BigDecimal odds,
            BigDecimal stake,
            String status,
            BigDecimal profit,
            BigDecimal returnAmount,
            Instant placedAt,
            Instant settledAt,
            Instant createdAt,
            Instant updatedAt) {

        BetRow withDimensions(
                String newSport, String newLeague, String newHome, String newAway,
                String newMarket, String newSelection) {
            return new BetRow(
                    betId, userId, newSport, newLeague, newHome, newAway, newMarket, newSelection,
                    odds, stake, status, profit, returnAmount, placedAt, settledAt, createdAt, updatedAt);
        }
    }
}
