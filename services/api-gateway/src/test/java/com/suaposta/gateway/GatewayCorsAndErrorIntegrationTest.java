package com.suaposta.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class GatewayCorsAndErrorIntegrationTest {

    private static final int GATEWAY_PORT = 8080;
    private static final int BETTING_SERVICE_PORT = 8082;
    private static final String APPROVED_FRONTEND_ORIGIN = "http://localhost:4200";
    private static final String DISALLOWED_ORIGIN = "https://malicious.example";
    private static final String VALID_AUTHORIZATION = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJiNDBkYTU4MC1hMDE3LTRhMTEtYmQ0Mi1jNjdhYTY0MDkxNjYiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6NDEwMjQ0NDgwMH0.zBIXoysIys5tveeN8Q_55fOeTMMI9IpcvY-jFHdGmro";
    private static final Path API_CONTRACT = Path.of("..", "..", "docs", "api-contracts.md");
    private static final Pattern HTTP_ENDPOINT = Pattern.compile(
            "(?m)^(GET|POST|PUT|PATCH|DELETE|OPTIONS)\\s+/\\S+.*$");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final RecordingService BETTING_SERVICE = new RecordingService();
    private static ConfigurableApplicationContext gatewayContext;

    @BeforeAll
    static void startGatewayAndServiceDouble() throws Exception {
        BETTING_SERVICE.start(BETTING_SERVICE_PORT);
        gatewayContext = ApplicationTestSupport.startApplication();
        assertThat(gatewayContext).isInstanceOf(WebServerApplicationContext.class);
        assertThat(((WebServerApplicationContext) gatewayContext).getWebServer().getPort())
                .isEqualTo(GATEWAY_PORT);
    }

    @AfterAll
    static void stopGatewayAndServiceDouble() {
        if (gatewayContext != null) {
            gatewayContext.close();
        }
        BETTING_SERVICE.stop();
    }

    @BeforeEach
    void resetServiceDouble() {
        BETTING_SERVICE.reset();
    }

    @Test
    void should_return_cors_headers_for_an_approved_frontend_origin_on_a_real_request() throws Exception {
        var response = getProtected("/bets", APPROVED_FRONTEND_ORIGIN);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue(APPROVED_FRONTEND_ORIGIN);
        assertThat(response.headers().allValues("Vary"))
                .anySatisfy(value -> assertThat(value).containsIgnoringCase("Origin"));
        assertThat(BETTING_SERVICE.requests()).isOne();
    }

    @Test
    void should_strip_sensitive_upstream_headers_from_successful_responses_and_preserve_external_headers()
            throws Exception {
        BETTING_SERVICE.respondWith(200, "{\"ok\":true}", java.util.Map.of(
                "Authorization", "Bearer upstream-secret",
                "Set-Cookie", "SESSION=internal-secret; Path=/; HttpOnly",
                "X-Internal-Service", "betting-service",
                "X-Upstream-Host", "betting-service:8082",
                "X-Service-Version", "internal-build-42"));

        var response = getProtected("/bets", APPROVED_FRONTEND_ORIGIN);
        var softly = new SoftAssertions();

        softly.assertThat(response.statusCode()).isEqualTo(200);
        softly.assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).containsIgnoringCase("application/json"));
        softly.assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue(APPROVED_FRONTEND_ORIGIN);
        softly.assertThat(response.headers().allValues("Vary"))
                .anySatisfy(value -> assertThat(value).containsIgnoringCase("Origin"));
        softly.assertThat(response.headers().firstValue("Authorization")).isEmpty();
        softly.assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        softly.assertThat(response.headers().firstValue("X-Internal-Service")).isEmpty();
        softly.assertThat(response.headers().firstValue("X-Upstream-Host")).isEmpty();
        softly.assertThat(response.headers().firstValue("X-Service-Version")).isEmpty();
        softly.assertAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH"})
    void should_accept_each_approved_http_method_on_a_preflight_request(String requestedMethod)
            throws Exception {
        var response = preflight("/bets", requestedMethod, APPROVED_FRONTEND_ORIGIN);

        assertThat(BETTING_SERVICE.requests()).isZero();
        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue(APPROVED_FRONTEND_ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods"))
                .hasValueSatisfying(value -> assertThat(value).contains(requestedMethod));
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers"))
                .hasValueSatisfying(value -> assertThat(value)
                        .containsIgnoringCase("Authorization")
                        .containsIgnoringCase("Content-Type"));
    }

    @Test
    void should_match_cors_allowed_methods_with_the_methods_documented_by_the_api_contract() throws Exception {
        var documentedMethods = documentedApiMethods();
        var response = preflight("/bets", "GET", APPROVED_FRONTEND_ORIGIN);

        // OPTIONS is the preflight transport method; the allow-list must match API methods.
        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods"))
                .hasValueSatisfying(value -> assertThat(corsMethods(value))
                        .containsExactlyInAnyOrderElementsOf(documentedMethods));
    }

    @Test
    void should_reject_a_real_request_from_an_unapproved_origin_before_forwarding() throws Exception {
        var response = getProtected("/bets", DISALLOWED_ORIGIN);

        assertThat(BETTING_SERVICE.requests()).isZero();
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void should_reject_a_preflight_request_for_a_disallowed_method_before_forwarding() throws Exception {
        var response = preflight("/bets", "DELETE", APPROVED_FRONTEND_ORIGIN);

        assertThat(BETTING_SERVICE.requests()).isZero();
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void should_reject_a_preflight_request_for_a_method_absent_from_the_api_contract() throws Exception {
        var methodAbsentFromContract = "TRACE";
        assertThat(documentedApiMethods()).doesNotContain(methodAbsentFromContract);

        var response = preflight("/bets", methodAbsentFromContract, APPROVED_FRONTEND_ORIGIN);

        assertThat(BETTING_SERVICE.requests()).isZero();
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void should_keep_unknown_gateway_errors_in_the_documented_safe_shape() throws Exception {
        var path = "/not-a-documented-route";
        var response = get(path, null);
        var body = response.body();

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(body)
                .contains("\"timestamp\"")
                .contains("\"status\":404")
                .contains("\"error\":\"Not Found\"")
                .contains("\"path\":\"" + path + "\"")
                .doesNotContain("java.lang.", "Exception", "\tat ", " at ", "password", "secret",
                        "credential", "Bearer",
                        "http://localhost:8081", "http://localhost:8082", "http://localhost:8083",
                        "auth-service", "betting-service", "analytics-service");
    }

    @Test
    void should_sanitize_an_external_service_error_into_a_stable_safe_contract() throws Exception {
        BETTING_SERVICE.respondWith(500, """
                {
                  "timestamp": "2026-08-07T12:00:00Z",
                  "status": 500,
                  "error": "java.lang.IllegalStateException",
                  "message": "database password=top-secret at http://betting-service:8082/internal/bets",
                  "path": "/internal/bets",
                  "service": "betting-service"
                }
                """);

        var response = getProtected("/bets", APPROVED_FRONTEND_ORIGIN);
        var body = response.body();

        assertThat(response.statusCode()).isBetween(500, 599);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).containsIgnoringCase("application/json"));
        assertThat(body)
                .contains("\"timestamp\"")
                .contains("\"status\"")
                .contains("\"error\"")
                .contains("\"message\"")
                .doesNotContain("java.lang.", "Exception", "\tat ", " at ", "password", "top-secret",
                        "credential", "Bearer", "http://betting-service:8082", "betting-service", "\"service\"");
        assertThat(body)
                .containsPattern("\\\"status\\\"\\s*:\\s*" + response.statusCode())
                .containsPattern("\\\"path\\\"\\s*:\\s*\\\"/bets\\\"");
    }

    @Test
    void should_sanitize_an_external_service_error_when_upstream_returns_sensitive_plain_text()
            throws Exception {
        BETTING_SERVICE.respondWith(502,
                "java.net.ConnectException: password=top-secret at http://betting-service:8082/internal/bets");

        var response = getProtected("/bets", APPROVED_FRONTEND_ORIGIN);
        var body = response.body();

        assertThat(response.statusCode()).isBetween(500, 599);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).containsIgnoringCase("application/json"));
        assertThat(body)
                .contains("\"timestamp\"")
                .contains("\"status\"")
                .contains("\"error\"")
                .contains("\"message\"")
                .doesNotContain("java.net.ConnectException", "password", "top-secret",
                        "http://betting-service:8082", "betting-service", "Bearer");
        assertThat(body)
                .containsPattern("\\\"status\\\"\\s*:\\s*" + response.statusCode())
                .containsPattern("\\\"path\\\"\\s*:\\s*\\\"/bets\\\"");
    }

    @Test
    void should_return_a_safe_gateway_error_when_upstream_connection_is_unavailable() throws Exception {
        BETTING_SERVICE.respondWithConnectionFailure();

        var response = getProtected("/bets", APPROVED_FRONTEND_ORIGIN);
        var body = response.body();
        var softly = new SoftAssertions();

        softly.assertThat(response.statusCode()).isBetween(500, 599);
        softly.assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).containsIgnoringCase("application/json"));
        softly.assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue(APPROVED_FRONTEND_ORIGIN);
        softly.assertThat(body)
                .doesNotContain("java.", "Exception", "ConnectException", "Connection refused",
                        "PrematureCloseException", "Caused by", "localhost:8082", "127.0.0.1",
                        "betting-service", "8082", "reactor.netty", "stacktrace");
        softly.assertThat(body)
                .contains("\"timestamp\"")
                .contains("\"status\"")
                .contains("\"error\"")
                .contains("\"message\"")
                .containsPattern("\\\"status\\\"\\s*:\\s*" + response.statusCode())
                .containsPattern("\\\"path\\\"\\s*:\\s*\\\"/bets\\\"");
        softly.assertAll();
    }

    private static Set<String> documentedApiMethods() throws IOException {
        var contract = Files.readString(API_CONTRACT);
        var methods = new TreeSet<String>();
        var matcher = HTTP_ENDPOINT.matcher(contract);
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        assertThat(methods)
                .as("API contract must document at least one HTTP endpoint")
                .isNotEmpty();
        return methods;
    }

    private static Set<String> corsMethods(String allowMethodsHeader) {
        var methods = new TreeSet<String>();
        Arrays.stream(allowMethodsHeader.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(methods::add);
        return methods;
    }

    private static HttpResponse<String> get(String path, String origin) throws Exception {
        return get(path, origin, null);
    }

    private static HttpResponse<String> getProtected(String path, String origin) throws Exception {
        return get(path, origin, VALID_AUTHORIZATION);
    }

    private static HttpResponse<String> get(String path, String origin, String authorization)
            throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + GATEWAY_PORT + path))
                .GET();
        if (origin != null) {
            builder.header("Origin", origin);
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return send(builder.build());
    }

    private static HttpResponse<String> preflight(String path, String requestedMethod, String origin)
            throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + GATEWAY_PORT + path))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", requestedMethod)
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build());
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class RecordingService {

        private final AtomicBoolean connectionFailure = new AtomicBoolean();
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicReference<ServiceResponse> response = new AtomicReference<>(
                new ServiceResponse(200, "{\"service\":\"BETTING_SERVICE\"}", java.util.Map.of()));
        private HttpServer server;

        private void start(int port) throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        private void handle(HttpExchange exchange) throws IOException {
            requests.incrementAndGet();
            if (connectionFailure.get()) {
                exchange.close();
                return;
            }
            var configuredResponse = response.get();
            var responseBytes = configuredResponse.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            configuredResponse.headers().forEach(exchange.getResponseHeaders()::set);
            exchange.sendResponseHeaders(configuredResponse.status(), responseBytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(responseBytes);
            }
        }

        private void respondWith(int status, String body) {
            respondWith(status, body, java.util.Map.of());
        }

        private void respondWith(int status, String body, java.util.Map<String, String> headers) {
            connectionFailure.set(false);
            response.set(new ServiceResponse(status, body, headers));
        }

        private void respondWithConnectionFailure() {
            connectionFailure.set(true);
        }

        private int requests() {
            return requests.get();
        }

        private void reset() {
            connectionFailure.set(false);
            requests.set(0);
            response.set(new ServiceResponse(200, "{\"service\":\"BETTING_SERVICE\"}", java.util.Map.of()));
        }

        private void stop() {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private record ServiceResponse(int status, String body, java.util.Map<String, String> headers) {
    }
}
