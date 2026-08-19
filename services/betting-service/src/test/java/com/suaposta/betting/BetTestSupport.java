package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

final class BetTestSupport {

    static final String BETS_PATH = "/bets";
    static final ObjectMapper JSON = new ObjectMapper()
            .setNodeFactory(new JsonNodeFactory(true));

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private BetTestSupport() {
    }

    static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(BettingServiceApplication.class)
                .sources(HistoricalPublisherTestConfiguration.class)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=" + databaseUrl(),
                        "spring.datasource.username=" + databaseUser(),
                        "spring.datasource.password=" + databasePassword())
                .run();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HistoricalPublisherTestConfiguration {

        @Bean
        @Primary
        BetEventPublisher historicalTestBetEventPublisher() {
            return org.mockito.Mockito.mock(BetEventPublisher.class);
        }
    }

    static ObjectNode validCreateRequest() {
        return createRequest(
                "FOOTBALL",
                "Brasileirão Série A",
                "Fortaleza",
                "Bahia",
                "MATCH_RESULT",
                "Fortaleza",
                new java.math.BigDecimal("2.10"),
                new java.math.BigDecimal("100.00"),
                "2026-07-21T20:30:00Z",
                "Home win based on recent form");
    }

    static ObjectNode createRequest(
            String sport,
            String league,
            String homeTeam,
            String awayTeam,
            String market,
            String selection,
            java.math.BigDecimal odds,
            java.math.BigDecimal stake,
            String placedAt,
            String notes) {
        var request = JSON.createObjectNode();
        request.put("sport", sport);
        request.put("league", league);
        request.put("homeTeam", homeTeam);
        request.put("awayTeam", awayTeam);
        request.put("market", market);
        request.put("selection", selection);
        request.put("odds", odds);
        request.put("stake", stake);
        request.put("placedAt", placedAt);
        request.put("notes", notes);
        return request;
    }

    static HttpResponse<String> createBet(
            ConfigurableApplicationContext context,
            UUID userId,
            ObjectNode body) throws Exception {
        return send(context, "POST", BETS_PATH, userId == null ? null : userId.toString(), body);
    }

    static HttpResponse<String> createBet(
            ConfigurableApplicationContext context,
            String userIdHeader,
            ObjectNode body) throws Exception {
        return send(context, "POST", BETS_PATH, userIdHeader, body);
    }

    static HttpResponse<String> listBets(
            ConfigurableApplicationContext context,
            UUID userId,
            Map<String, String> queryParameters) throws Exception {
        var query = queryParameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        var path = query.isEmpty() ? BETS_PATH : BETS_PATH + "?" + query;
        return send(context, "GET", path, userId == null ? null : userId.toString(), null);
    }

    static HttpResponse<String> listBets(
            ConfigurableApplicationContext context,
            String userIdHeader,
            Map<String, String> queryParameters) throws Exception {
        var query = queryParameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        var path = query.isEmpty() ? BETS_PATH : BETS_PATH + "?" + query;
        return send(context, "GET", path, userIdHeader, null);
    }

    static HttpResponse<String> getBet(
            ConfigurableApplicationContext context,
            UUID userId,
            String betId) throws Exception {
        return send(
                context,
                "GET",
                BETS_PATH + "/" + betId,
                userId == null ? null : userId.toString(),
                null);
    }

    static HttpResponse<String> getBet(
            ConfigurableApplicationContext context,
            String userIdHeader,
            String betId) throws Exception {
        return send(context, "GET", BETS_PATH + "/" + betId, userIdHeader, null);
    }

    static JsonNode json(HttpResponse<String> response) throws Exception {
        assertThat(response.body()).as("API response must be valid JSON").isNotBlank();
        return JSON.readTree(response.body());
    }

    static UUID responseBetId(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode())
                .as("creating a fixture must return the documented 201 response")
                .isEqualTo(201);
        var id = json(response).get("id");
        assertThat(id).as("created Bet response must contain id").isNotNull();
        return UUID.fromString(id.asText());
    }

    static Set<String> fieldNames(JsonNode node) {
        var names = new HashSet<String>();
        Iterator<String> fields = node.fieldNames();
        fields.forEachRemaining(names::add);
        return names;
    }

    static void assertCreatedBetResponse(
            HttpResponse<String> response,
            UUID expectedUserId,
            ObjectNode request,
            String expectedStake,
            String expectedOdds) throws Exception {
        assertThat(response.statusCode()).isEqualTo(201);

        var body = json(response);
        assertThat(body.isObject()).isTrue();
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "id",
                "userId",
                "sport",
                "league",
                "homeTeam",
                "awayTeam",
                "market",
                "selection",
                "odds",
                "stake",
                "status",
                "profit",
                "returnAmount",
                "placedAt",
                "settledAt",
                "notes",
                "createdAt",
                "updatedAt");
        assertThat(UUID.fromString(body.get("id").asText())).isNotNull();
        assertThat(UUID.fromString(body.get("userId").asText())).isEqualTo(expectedUserId);
        assertThat(body.get("sport").asText()).isEqualTo(request.get("sport").asText());
        assertThat(body.get("league").asText()).isEqualTo(request.get("league").asText());
        assertThat(body.get("homeTeam").asText()).isEqualTo(request.get("homeTeam").asText());
        assertThat(body.get("awayTeam").asText()).isEqualTo(request.get("awayTeam").asText());
        assertThat(body.get("market").asText()).isEqualTo(request.get("market").asText());
        assertThat(body.get("selection").asText()).isEqualTo(request.get("selection").asText());
        assertDecimalValue(body.get("odds"), expectedOdds);
        assertDecimalValue(body.get("stake"), expectedStake);
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("profit").isNull()).isTrue();
        assertThat(body.get("returnAmount").isNull()).isTrue();
        assertThat(body.get("placedAt").asText()).isEqualTo(request.get("placedAt").asText());
        assertThat(body.get("settledAt").isNull()).isTrue();
        assertThat(body.get("notes").asText()).isEqualTo(request.get("notes").asText());
        assertThat(Instant.parse(body.get("createdAt").asText())).isNotNull();
        assertThat(Instant.parse(body.get("updatedAt").asText())).isNotNull();
        assertSafeResponse(response);
    }

    static void assertRetrievedBetResponse(
            HttpResponse<String> response,
            UUID expectedUserId,
            ObjectNode request,
            String expectedStake,
            String expectedOdds) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);

        var body = json(response);
        assertThat(body.isObject()).isTrue();
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "id",
                "userId",
                "sport",
                "league",
                "homeTeam",
                "awayTeam",
                "market",
                "selection",
                "odds",
                "stake",
                "status",
                "profit",
                "returnAmount",
                "placedAt",
                "settledAt",
                "notes",
                "createdAt",
                "updatedAt");
        assertThat(UUID.fromString(body.get("id").asText())).isNotNull();
        assertThat(UUID.fromString(body.get("userId").asText())).isEqualTo(expectedUserId);
        assertThat(body.get("sport").asText()).isEqualTo(request.get("sport").asText());
        assertThat(body.get("league").asText()).isEqualTo(request.get("league").asText());
        assertThat(body.get("homeTeam").asText()).isEqualTo(request.get("homeTeam").asText());
        assertThat(body.get("awayTeam").asText()).isEqualTo(request.get("awayTeam").asText());
        assertThat(body.get("market").asText()).isEqualTo(request.get("market").asText());
        assertThat(body.get("selection").asText()).isEqualTo(request.get("selection").asText());
        assertDecimalValue(body.get("odds"), expectedOdds);
        assertDecimalValue(body.get("stake"), expectedStake);
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("profit").isNull()).isTrue();
        assertThat(body.get("returnAmount").isNull()).isTrue();
        assertThat(body.get("placedAt").asText()).isEqualTo(request.get("placedAt").asText());
        assertThat(body.get("settledAt").isNull()).isTrue();
        assertThat(body.get("notes").asText()).isEqualTo(request.get("notes").asText());
        assertThat(Instant.parse(body.get("createdAt").asText())).isNotNull();
        assertThat(Instant.parse(body.get("updatedAt").asText())).isNotNull();
        assertSafeResponse(response);
    }

    static void assertListEnvelope(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json(response);
        assertThat(body.isObject()).isTrue();
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "content", "page", "size", "totalElements", "totalPages");
        assertThat(body.get("content").isArray()).isTrue();
        assertThat(body.get("page").isIntegralNumber()).isTrue();
        assertThat(body.get("size").isIntegralNumber()).isTrue();
        assertThat(body.get("totalElements").isIntegralNumber()).isTrue();
        assertThat(body.get("totalPages").isIntegralNumber()).isTrue();
        assertSafeResponse(response);
    }

    static void assertListItem(JsonNode item, ObjectNode request, String expectedStake, String expectedOdds) {
        assertThat(fieldNames(item)).containsExactlyInAnyOrder(
                "id",
                "sport",
                "league",
                "homeTeam",
                "awayTeam",
                "market",
                "selection",
                "odds",
                "stake",
                "status",
                "profit",
                "returnAmount",
                "placedAt",
                "settledAt");
        assertThat(UUID.fromString(item.get("id").asText())).isNotNull();
        assertThat(item.get("sport").asText()).isEqualTo(request.get("sport").asText());
        assertThat(item.get("league").asText()).isEqualTo(request.get("league").asText());
        assertThat(item.get("homeTeam").asText()).isEqualTo(request.get("homeTeam").asText());
        assertThat(item.get("awayTeam").asText()).isEqualTo(request.get("awayTeam").asText());
        assertThat(item.get("market").asText()).isEqualTo(request.get("market").asText());
        assertThat(item.get("selection").asText()).isEqualTo(request.get("selection").asText());
        assertDecimalValue(item.get("odds"), expectedOdds);
        assertDecimalValue(item.get("stake"), expectedStake);
        assertThat(item.get("status").asText()).isEqualTo("PENDING");
        assertThat(item.get("profit").isNull()).isTrue();
        assertThat(item.get("returnAmount").isNull()).isTrue();
        assertThat(item.get("placedAt").asText()).isEqualTo(request.get("placedAt").asText());
        assertThat(item.get("settledAt").isNull()).isTrue();
    }

    static void assertErrorEnvelope(HttpResponse<String> response, int expectedStatus, String expectedPath)
            throws Exception {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        var body = json(response);
        assertThat(body.isObject()).isTrue();
        assertThat(fieldNames(body)).contains("timestamp", "status", "error", "message", "path");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(Instant.parse(body.get("timestamp").asText())).isNotNull();
        assertThat(body.get("status").asInt()).isEqualTo(expectedStatus);
        assertThat(body.get("path").asText()).isEqualTo(expectedPath);
        assertSafeResponse(response);
    }

    static void assertSafeResponse(HttpResponse<String> response) {
        var lowerBody = response.body().toLowerCase(java.util.Locale.ROOT);
        assertThat(lowerBody)
                .doesNotContain(
                        "authorization",
                        "x-user-id",
                        "jwt",
                        "credential",
                        "exception",
                        "stacktrace",
                        "stack trace",
                        "org.springframework",
                        "org.hibernate",
                        "java.lang",
                        "jdbc",
                        "postgres",
                        "database",
                        "com.suaposta",
                        "betentity",
                        "jpa",
                        "password");
    }

    static void assertDecimalValue(JsonNode node, String expected) {
        assertThat(node.isNumber()).isTrue();
        assertThat(node.decimalValue()).isEqualByComparingTo(new java.math.BigDecimal(expected));
    }

    private static HttpResponse<String> send(
            ConfigurableApplicationContext context,
            String method,
            String path,
            String userIdHeader,
            ObjectNode body) throws Exception {
        var webContext = (WebServerApplicationContext) context;
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + webContext.getWebServer().getPort() + path));
        if (userIdHeader != null) {
            builder.header("X-User-Id", userIdHeader);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        var publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body.toString());
        var request = builder.method(method, publisher).build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String databaseUrl() {
        return setting(
                "betting.test.db.url",
                "BETTING_DB_JDBC_URL",
                "jdbc:postgresql://"
                        + setting("betting.test.db.host", "POSTGRES_HOST", "127.0.0.1")
                        + ":"
                        + setting("betting.test.db.port", "POSTGRES_HOST_PORT", "5432")
                        + "/"
                        + setting("betting.test.db.name", "BETTING_DB_NAME", "suaposta_betting"));
    }

    private static String databaseUser() {
        return setting("betting.test.db.user", "BETTING_DB_USER", "suaposta_betting");
    }

    private static String databasePassword() {
        return setting("betting.test.db.password", "BETTING_DB_PASSWORD", "change_me_betting");
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
}
