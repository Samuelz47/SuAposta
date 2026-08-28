    package com.suaposta.analytics;

    import static org.assertj.core.api.Assertions.assertThat;

    import com.fasterxml.jackson.databind.JsonNode;
    import java.io.IOException;
    import java.net.URI;
    import java.net.http.HttpClient;
    import java.net.http.HttpRequest;
    import java.net.http.HttpResponse;
    import java.sql.SQLException;
    import java.util.Set;
    import java.util.UUID;
    import org.springframework.boot.web.context.WebServerApplicationContext;
    import org.springframework.context.ConfigurableApplicationContext;
    import org.springframework.web.util.UriComponentsBuilder;
    import org.testcontainers.containers.PostgreSQLContainer;

    final class Task73TestSupport {

        static final UUID USER_A = Task71TestSupport.USER_A;
        static final UUID USER_B = Task71TestSupport.USER_B;
        static final Set<String> ITEM_FIELDS = Set.of(
                "name", "totalStake", "profit", "roi", "yield", "winRate", "avgOdds", "drawdown",
                "betsCount", "pendingCount", "wonCount", "lostCount", "voidCount", "cashoutCount",
                "cancelledCount");

        private static final HttpClient HTTP = HttpClient.newHttpClient();

        private Task73TestSupport() {
        }

        static ConfigurableApplicationContext startApplication(PostgreSQLContainer<?> postgres) {
            return Task71TestSupport.startApplication(postgres);
        }

        static void resetDatabase(PostgreSQLContainer<?> postgres) throws SQLException {
            Task71TestSupport.resetDatabase(postgres);
        }

        static void insert(PostgreSQLContainer<?> postgres, Task71TestSupport.BetRow row) throws SQLException {
            Task71TestSupport.insertBet(postgres, row);
        }

        static HttpResponse<String> breakdown(
                ConfigurableApplicationContext context, String userId, String query)
                throws IOException, InterruptedException {
            var port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/analytics/performance/breakdown" + query))
                    .GET();
            if (userId != null) {
                builder.header("X-User-Id", userId);
            }
            return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        static JsonNode body(HttpResponse<String> response) throws IOException {
            return Task71TestSupport.JSON.readTree(response.body());
        }

        static String query(Object... pairs) {
            var builder = UriComponentsBuilder.newInstance();
            for (var i = 0; i < pairs.length; i += 2) {
                builder.queryParam((String) pairs[i], pairs[i + 1]);
            }
            var query = builder.build().encode().getQuery();
            return query == null ? "" : "?" + query;
        }

        static void assertSafeError(HttpResponse<String> response, int status) throws IOException {
            assertThat(response.statusCode()).isEqualTo(status);
            var error = body(response);
            assertThat(error.path("status").intValue()).isEqualTo(status);
            assertThat(error.path("timestamp").isTextual()).isTrue();
            assertThat(error.path("error").isTextual()).isTrue();
            assertThat(error.path("message").isTextual()).isTrue();
            assertThat(error.path("path").asText()).isEqualTo("/analytics/performance/breakdown");
            assertThat(response.body()).doesNotContain(
                    "JdbcTemplate", "SQLException", "analytics_bets", "processed_events", "stackTrace");
        }

        static void assertExactFields(JsonNode object, Set<String> expected) {
            assertThat(object.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(expected);
        }

        static void assertMetric(JsonNode item, String field, String expected, int scale) {
            assertThat(item.path(field).isNumber()).as(field + " must be numeric").isTrue();
            assertThat(item.path(field).decimalValue()).isEqualByComparingTo(expected);
            assertThat(item.path(field).decimalValue().scale()).as(field + " scale").isEqualTo(scale);
        }

        static void assertCount(JsonNode item, String field, int expected) {
            assertThat(item.path(field).isIntegralNumber()).as(field + " must be an integer").isTrue();
            assertThat(item.path(field).intValue()).isEqualTo(expected);
        }
    }
