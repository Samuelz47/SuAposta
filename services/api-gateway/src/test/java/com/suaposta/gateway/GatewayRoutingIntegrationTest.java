package com.suaposta.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class GatewayRoutingIntegrationTest {

    private static final int GATEWAY_PORT = 8080;
    private static final String VALID_AUTHORIZATION = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJiNDBkYTU4MC1hMDE3LTRhMTEtYmQ0Mi1jNjdhYTY0MDkxNjYiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6NDEwMjQ0NDgwMH0.zBIXoysIys5tveeN8Q_55fOeTMMI9IpcvY-jFHdGmro";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Map<String, RecordingService> SERVICES = Map.of(
            "auth", new RecordingService("AUTH_SERVICE"),
            "betting", new RecordingService("BETTING_SERVICE"),
            "analytics", new RecordingService("ANALYTICS_SERVICE"));

    private static ConfigurableApplicationContext gatewayContext;

    @BeforeAll
    static void startGatewayAndServiceDoubles() throws Exception {
        SERVICES.values().forEach(GatewayRoutingIntegrationTest::startService);
        gatewayContext = ApplicationTestSupport.startApplication(Map.of(
                "AUTH_SERVICE_URL", SERVICES.get("auth").url(),
                "BETTING_SERVICE_URL", SERVICES.get("betting").url(),
                "ANALYTICS_SERVICE_URL", SERVICES.get("analytics").url()));
        assertThat(gatewayContext).isInstanceOf(WebServerApplicationContext.class);
        assertThat(((WebServerApplicationContext) gatewayContext).getWebServer().getPort())
                .isEqualTo(GATEWAY_PORT);
    }

    @AfterAll
    static void stopGatewayAndServiceDoubles() {
        if (gatewayContext != null) {
            gatewayContext.close();
        }
        SERVICES.values().forEach(RecordingService::stop);
    }

    @BeforeEach
    void resetServiceObservations() {
        SERVICES.values().forEach(RecordingService::reset);
    }

    @Test
    void should_route_auth_paths_only_to_auth_service_when_frontend_calls_gateway() throws Exception {
        var response = post("/auth/login");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("AUTH_SERVICE").contains("/auth/login");
        assertThat(SERVICES.get("auth").requests()).isEqualTo(1);
        assertThat(SERVICES.get("betting").requests()).isZero();
        assertThat(SERVICES.get("analytics").requests()).isZero();
    }

    @Test
    void should_route_bets_paths_only_to_betting_service_when_frontend_calls_gateway() throws Exception {
        var response = get("/bets?status=PENDING", VALID_AUTHORIZATION);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("BETTING_SERVICE").contains("/bets");
        assertThat(SERVICES.get("auth").requests()).isZero();
        assertThat(SERVICES.get("betting").requests()).isEqualTo(1);
        assertThat(SERVICES.get("analytics").requests()).isZero();
    }

    @Test
    void should_route_analytics_paths_only_to_analytics_service_when_frontend_calls_gateway() throws Exception {
        var response = get("/analytics/dashboard", VALID_AUTHORIZATION);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("ANALYTICS_SERVICE").contains("/analytics/dashboard");
        assertThat(SERVICES.get("auth").requests()).isZero();
        assertThat(SERVICES.get("betting").requests()).isZero();
        assertThat(SERVICES.get("analytics").requests()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/authentic/login", "/betting", "/analytic/dashboard"})
    void should_not_capture_an_unknown_service_prefix_with_another_service_route(String path) throws Exception {
        var response = get(path);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(SERVICES.values()).allSatisfy(service -> assertThat(service.requests()).isZero());
    }

    @Test
    void should_return_the_documented_error_shape_for_an_unknown_path() throws Exception {
        var path = "/not-a-documented-route";
        var response = get(path);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("\"timestamp\"")
                .contains("\"status\":404")
                .contains("\"error\":\"Not Found\"")
                .contains("\"path\":\"" + path + "\"");
        assertThat(SERVICES.values()).allSatisfy(service -> assertThat(service.requests()).isZero());
    }

    private static void startService(RecordingService recordingService) {
        try {
            recordingService.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start service double", exception);
        }
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    private static HttpResponse<String> get(String path, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + GATEWAY_PORT + path))
                .GET();
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return send(builder.build());
    }

    private static HttpResponse<String> post(String path) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + GATEWAY_PORT + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build());
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class RecordingService {

        private final String serviceName;
        private final AtomicInteger requests = new AtomicInteger();
        private HttpServer server;

        private RecordingService(String serviceName) {
            this.serviceName = serviceName;
        }

        private void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        private String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            requests.incrementAndGet();
            var path = exchange.getRequestURI().getPath();
            var responseBody = "{\"service\":\"" + serviceName + "\",\"path\":\"" + path + "\"}";
            var responseBytes = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(responseBytes);
            }
        }

        private int requests() {
            return requests.get();
        }

        private void reset() {
            requests.set(0);
        }

        private void stop() {
            if (server != null) {
                server.stop(0);
            }
        }
    }
}
