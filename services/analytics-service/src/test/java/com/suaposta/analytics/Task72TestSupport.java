package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

final class Task72TestSupport {

    static final Set<String> POINT_FIELDS = Set.of("date", "profit", "cumulativeProfit", "bankroll");
    static final UUID USER_A = Task71TestSupport.USER_A;
    static final UUID USER_B = Task71TestSupport.USER_B;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private Task72TestSupport() {
    }

    static HttpResponse<String> bankrollEvolution(
            ConfigurableApplicationContext context, String userId, String query) throws IOException, InterruptedException {
        var port = ((org.springframework.boot.web.context.WebServerApplicationContext) context)
                .getWebServer().getPort();
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/analytics/bankroll-evolution" + query))
                .GET();
        if (userId != null) {
            requestBuilder.header("X-User-Id", userId);
        }
        return HTTP.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode body(HttpResponse<String> response) throws IOException {
        return Task71TestSupport.JSON.readTree(response.body());
    }

    static void assertSafeError(HttpResponse<String> response, int status) throws IOException {
        assertThat(response.statusCode()).isEqualTo(status);
        var error = body(response);
        assertThat(error.path("status").intValue()).isEqualTo(status);
        assertThat(error.path("timestamp").isTextual()).isTrue();
        assertThat(error.path("error").isTextual()).isTrue();
        assertThat(error.path("message").isTextual()).isTrue();
        assertThat(error.path("path").asText()).isEqualTo("/analytics/bankroll-evolution");
        assertThat(response.body()).doesNotContain(
                "JdbcTemplate", "SQLException", "analytics_bets", "processed_events", "stackTrace");
    }

    static void assertExactPointFields(JsonNode point) {
        assertThat(point.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(POINT_FIELDS);
    }

    static void assertMoney(JsonNode point, String field, String expected) {
        assertThat(point.path(field).isNumber()).as(field + " must be numeric").isTrue();
        assertThat(point.path(field).decimalValue()).isEqualByComparingTo(expected);
        assertThat(point.path(field).decimalValue().scale()).as(field + " scale").isEqualTo(2);
    }

    static void insert(
            PostgreSQLContainer<?> postgres,
            String betId,
            UUID userId,
            String status,
            String profit,
            String placedAt,
            String settledAt,
            String sport,
            String league,
            String homeTeam,
            String awayTeam,
            String market) throws SQLException {
        insert(postgres, betId, userId, status, profit, placedAt, settledAt,
                sport, league, homeTeam, awayTeam, market, "Selection");
    }

    static void insert(
            PostgreSQLContainer<?> postgres,
            String betId,
            UUID userId,
            String status,
            String profit,
            String placedAt,
            String settledAt,
            String sport,
            String league,
            String homeTeam,
            String awayTeam,
            String market,
            String selection) throws SQLException {
        var row = Task71TestSupport.row(
                betId, userId, status, "100.00", "2.0000", profit,
                profit == null ? null : new java.math.BigDecimal("100.00")
                        .add(new java.math.BigDecimal(profit)).toPlainString(),
                placedAt, settledAt).withDimensions(sport, league, homeTeam, awayTeam, market, selection);
        Task71TestSupport.insertBet(postgres, row);
    }

    static String query(Object... pairs) {
        var builder = UriComponentsBuilder.newInstance();
        for (var i = 0; i < pairs.length; i += 2) {
            builder.queryParam((String) pairs[i], pairs[i + 1]);
        }
        var query = builder.build().encode().getQuery();
        return query == null ? "" : "?" + query;
    }

    static Instant instant(String value) {
        return Instant.parse(value);
    }
}
