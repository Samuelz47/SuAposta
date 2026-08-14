package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.context.WebServerApplicationContext;

final class BetTask53HttpTestSupport {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private BetTask53HttpTestSupport() {
    }

    static ObjectNode validUpdateRequest() {
        var request = BetTestSupport.JSON.createObjectNode();
        request.put("sport", "TENNIS");
        request.put("league", "ATP");
        request.put("homeTeam", "Player A");
        request.put("awayTeam", "Player B");
        request.put("market", "MATCH_WINNER");
        request.put("selection", "Player A");
        request.put("odds", new java.math.BigDecimal("2.12555"));
        request.put("stake", new java.math.BigDecimal("120.126"));
        request.put("placedAt", "2026-07-22T20:30:00Z");
        request.put("notes", "updated notes");
        return request;
    }

    static ObjectNode settlement(String status) {
        var request = BetTestSupport.JSON.createObjectNode();
        request.put("status", status);
        return request;
    }

    static ObjectNode cashout(String value) {
        var request = settlement("CASHOUT");
        request.put("returnAmount", new java.math.BigDecimal(value));
        return request;
    }

    static HttpResponse<String> updateBet(
            ConfigurableApplicationContext context, UUID userId, String betId, ObjectNode body)
            throws Exception {
        return send(context, "PUT", "/bets/" + betId, userId == null ? null : userId.toString(), body);
    }

    static HttpResponse<String> updateBet(
            ConfigurableApplicationContext context, String userIdHeader, String betId, ObjectNode body)
            throws Exception {
        return send(context, "PUT", "/bets/" + betId, userIdHeader, body);
    }

    static HttpResponse<String> settleBet(
            ConfigurableApplicationContext context, UUID userId, String betId, ObjectNode body)
            throws Exception {
        return send(context, "PATCH", "/bets/" + betId + "/settle",
                userId == null ? null : userId.toString(), body);
    }

    static HttpResponse<String> settleBet(
            ConfigurableApplicationContext context, String userIdHeader, String betId, ObjectNode body)
            throws Exception {
        return send(context, "PATCH", "/bets/" + betId + "/settle", userIdHeader, body);
    }

    static JsonNode json(HttpResponse<String> response) throws Exception {
        return BetTestSupport.json(response);
    }

    static void assertPendingUpdateResponse(
            HttpResponse<String> response, UUID userId, UUID betId, ObjectNode request, JsonNode before)
            throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json(response);
        assertThat(body.get("id").asText()).isEqualTo(betId.toString());
        assertThat(body.get("userId").asText()).isEqualTo(userId.toString());
        for (var field : new String[]{"sport", "league", "homeTeam", "awayTeam", "market", "selection", "placedAt", "notes"}) {
            assertThat(body.get(field).asText()).isEqualTo(request.get(field).asText());
        }
        assertThat(body.get("odds").decimalValue()).isEqualByComparingTo("2.1256");
        assertThat(body.get("stake").decimalValue()).isEqualByComparingTo("120.13");
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("profit").isNull()).isTrue();
        assertThat(body.get("returnAmount").isNull()).isTrue();
        assertThat(body.get("settledAt").isNull()).isTrue();
        assertThat(body.get("createdAt").asText()).isEqualTo(before.get("createdAt").asText());
        assertThat(Instant.parse(body.get("updatedAt").asText()))
                .isAfterOrEqualTo(Instant.parse(before.get("updatedAt").asText()));
        assertSafe(response);
    }

    static void assertSettledResponse(
            HttpResponse<String> response, UUID userId, UUID betId, String status,
            String returnAmount, String profit, String stake, JsonNode before) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json(response);
        assertThat(body.get("id").asText()).isEqualTo(betId.toString());
        assertThat(body.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(body.get("status").asText()).isEqualTo(status);
        assertThat(body.get("returnAmount").decimalValue()).isEqualByComparingTo(returnAmount);
        assertThat(body.get("profit").decimalValue()).isEqualByComparingTo(profit);
        assertThat(body.get("stake").decimalValue()).isEqualByComparingTo(stake);
        assertThat(body.get("settledAt").isTextual()).isTrue();
        assertThat(body.get("createdAt").asText()).isEqualTo(before.get("createdAt").asText());
        assertThat(Instant.parse(body.get("updatedAt").asText()))
                .isAfterOrEqualTo(Instant.parse(before.get("updatedAt").asText()));
        assertSafe(response);
    }

    static void assertError(HttpResponse<String> response, int status, String path) throws Exception {
        BetTestSupport.assertErrorEnvelope(response, status, path);
    }

    static String normalizedError(HttpResponse<String> response) throws Exception {
        var body = ((ObjectNode) json(response)).deepCopy();
        body.remove("timestamp");
        body.remove("path");
        return body.toString();
    }

    static void assertSafe(HttpResponse<String> response) {
        BetTestSupport.assertSafeResponse(response);
    }

    private static HttpResponse<String> send(
            ConfigurableApplicationContext context, String method, String path,
            String userIdHeader, ObjectNode body) throws Exception {
        var webContext = (WebServerApplicationContext) context;
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + webContext.getWebServer().getPort() + path))
                .header("Content-Type", "application/json");
        if (userIdHeader != null) {
            builder.header("X-User-Id", userIdHeader);
        }
        var request = builder
                .method(method, HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
