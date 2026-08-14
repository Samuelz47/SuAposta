package com.suaposta.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.stream.Stream;

class GatewayJwtAuthenticationIntegrationTest {

    private static final int GATEWAY_PORT = 8080;
    private static final String TEST_SECRET = "task-3-3-only-signing-secret-32-bytes";
    private static final String USER_ID = "b40da580-a017-4a11-bd42-c67aa6409166";
    private static final String OTHER_USER_ID = "f8c6eb32-54d4-4024-9581-7a0a8d6f4f19";
    private static final String TOKEN_WITH_EXTRA_CLAIMS = signedToken(
            Map.of(
                    "sub", USER_ID,
                    "email", "claims-must-not-leak@example.com",
                    "role", "ADMIN"),
            Instant.now().minusSeconds(30),
            Instant.now().plusSeconds(3600),
            TEST_SECRET);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final RecordingService AUTH_SERVICE = new RecordingService("AUTH_SERVICE");
    private static final RecordingService BETTING_SERVICE = new RecordingService("BETTING_SERVICE");
    private static ConfigurableApplicationContext gatewayContext;

    @BeforeAll
    static void startGatewayAndServiceDoubles() throws Exception {
        AUTH_SERVICE.start();
        BETTING_SERVICE.start();
        gatewayContext = ApplicationTestSupport.startApplication(Map.of(
                "AUTH_SERVICE_URL", AUTH_SERVICE.url(),
                "BETTING_SERVICE_URL", BETTING_SERVICE.url()));
        assertThat(gatewayContext).isInstanceOf(WebServerApplicationContext.class);
        assertThat(((WebServerApplicationContext) gatewayContext).getWebServer().getPort())
                .isEqualTo(GATEWAY_PORT);
    }

    @AfterAll
    static void stopGatewayAndServiceDoubles() {
        if (gatewayContext != null) {
            gatewayContext.close();
        }
        AUTH_SERVICE.stop();
        BETTING_SERVICE.stop();
    }

    @BeforeEach
    void resetServiceObservations() {
        AUTH_SERVICE.reset();
        BETTING_SERVICE.reset();
    }

    @ParameterizedTest(name = "{0} remains public")
    @MethodSource("publicAuthenticationEndpoints")
    void should_keep_documented_public_authentication_endpoint_accessible_without_bearer_token(
            String caseName, String method, String path) throws Exception {
        var response = send(request(method, path, null));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(AUTH_SERVICE.requests()).isOne();
        assertThat(AUTH_SERVICE.lastPath()).isEqualTo(path);
        assertThat(BETTING_SERVICE.requests()).isZero();
    }

    @Test
    void should_reject_a_protected_route_without_authorization_before_forwarding() throws Exception {
        var response = send(request("GET", "/bets", null));

        assertUnauthorized(response, null);
        assertThat(BETTING_SERVICE.requests()).isZero();
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidAuthorizationHeaders")
    void should_reject_a_protected_route_when_authorization_is_not_a_valid_bearer_jwt(
            String caseName, String authorizationHeader) throws Exception {
        var response = send(request("GET", "/bets", authorizationHeader));

        assertUnauthorized(response, authorizationHeader);
        assertThat(BETTING_SERVICE.requests()).isZero();
    }

    @Test
    void should_forward_a_valid_jwt_to_the_correct_downstream_service() throws Exception {
        var response = send(request("GET", "/bets", "Bearer " + TOKEN_WITH_EXTRA_CLAIMS));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("BETTING_SERVICE");
        assertThat(BETTING_SERVICE.requests()).isOne();
        assertThat(BETTING_SERVICE.lastPath()).isEqualTo("/bets");
        assertThat(AUTH_SERVICE.requests()).isZero();
    }

    @Test
    void should_propagate_the_authenticated_user_id_to_the_downstream_service() throws Exception {
        var response = send(request("GET", "/bets", "Bearer " + TOKEN_WITH_EXTRA_CLAIMS));
        var downstreamHeaders = BETTING_SERVICE.lastHeaders();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(downstreamHeaders.getFirst("X-User-Id")).isEqualTo(USER_ID);
    }

    @Test
    void should_not_propagate_the_raw_jwt_to_the_downstream_service() throws Exception {
        var response = send(request("GET", "/bets", "Bearer " + TOKEN_WITH_EXTRA_CLAIMS));
        var downstreamHeaders = BETTING_SERVICE.lastHeaders();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(downstreamHeaders.getFirst("Authorization")).isNull();
        assertThat(downstreamHeaders.values().stream().flatMap(List::stream).toList())
                .allSatisfy(value -> assertThat(value).doesNotContain(TOKEN_WITH_EXTRA_CLAIMS));
    }

    @Test
    void should_not_forward_a_client_supplied_user_id_to_a_public_authentication_endpoint()
            throws Exception {
        var response = send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + GATEWAY_PORT + "/auth/login"))
                .header("X-User-Id", OTHER_USER_ID)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(AUTH_SERVICE.lastHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void should_not_turn_unneeded_jwt_claims_into_internal_headers() throws Exception {
        var response = send(request("GET", "/bets", "Bearer " + TOKEN_WITH_EXTRA_CLAIMS));
        var downstreamHeaders = BETTING_SERVICE.lastHeaders();
        var downstreamValues = downstreamHeaders.values().stream()
                .flatMap(List::stream)
                .toList();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(downstreamHeaders.keySet())
                .doesNotContain("X-User-Email", "X-User-Role");
        assertThat(downstreamValues)
                .allSatisfy(value -> assertThat(value)
                        .doesNotContain("claims-must-not-leak@example.com")
                        .doesNotContain("ADMIN"));
    }

    @Test
    void should_not_make_an_ownership_decision_at_the_gateway_for_an_authenticated_request()
            throws Exception {
        var requestedBetOwnedByAnotherUser = "/bets/" + OTHER_USER_ID;
        var response = send(request("GET", requestedBetOwnedByAnotherUser,
                "Bearer " + TOKEN_WITH_EXTRA_CLAIMS));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(BETTING_SERVICE.requests()).isOne();
        assertThat(BETTING_SERVICE.lastPath()).isEqualTo(requestedBetOwnedByAnotherUser);
    }

    private static Stream<Arguments> publicAuthenticationEndpoints() {
        return Stream.of(
                Arguments.of("POST /auth/register", "POST", "/auth/register"),
                Arguments.of("POST /auth/login", "POST", "/auth/login"));
    }

    private static Stream<Arguments> invalidAuthorizationHeaders() {
        var malformedToken = "not-a-jwt";
        var invalidSignatureToken = signedToken(
                Map.of("sub", USER_ID), Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(3600), "wrong-test-secret");
        var expiredToken = signedToken(
                Map.of("sub", USER_ID), Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(1), TEST_SECRET);
        var nonHs256Token = signedToken(
                Map.of("sub", USER_ID), Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(3600), TEST_SECRET, "HS384", "HmacSHA384");
        var missingSubjectToken = signedToken(
                Map.of(), Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(3600), TEST_SECRET);
        var missingIssuedAtToken = signedToken(
                Map.of("sub", USER_ID), null, Instant.now().plusSeconds(3600), TEST_SECRET);
        var missingExpirationToken = signedToken(
                Map.of("sub", USER_ID), Instant.now().minusSeconds(30), null, TEST_SECRET);
        var nonUuidSubjectToken = signedToken(
                Map.of("sub", "not-a-user-uuid"), Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(3600), TEST_SECRET);

        return Stream.of(
                Arguments.of("different authorization scheme", "Basic dXNlcjpwYXNz"),
                Arguments.of("empty bearer value", "Bearer "),
                Arguments.of("malformed JWT", "Bearer " + malformedToken),
                Arguments.of("unsupported JWT algorithm", "Bearer " + nonHs256Token),
                Arguments.of("invalid JWT signature", "Bearer " + invalidSignatureToken),
                Arguments.of("expired JWT", "Bearer " + expiredToken),
                Arguments.of("missing sub claim", "Bearer " + missingSubjectToken),
                Arguments.of("missing iat claim", "Bearer " + missingIssuedAtToken),
                Arguments.of("missing exp claim", "Bearer " + missingExpirationToken),
                Arguments.of("sub claim is not a UUID", "Bearer " + nonUuidSubjectToken));
    }

    private static HttpRequest request(String method, String path, String authorizationHeader) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + GATEWAY_PORT + path));
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }
        return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertUnauthorized(HttpResponse<String> response, String rejectedToken) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).containsIgnoringCase("application/json"));
        assertThat(response.body())
                .contains("\"timestamp\"")
                .contains("\"status\"")
                .contains("\"error\"")
                .contains("\"message\"")
                .contains("\"path\"")
                .contains("/bets")
                .doesNotContain("java.", "Exception", "stacktrace", "private key", TEST_SECRET,
                        "password", "secret", "credential");
        if (rejectedToken != null) {
            assertThat(response.body()).doesNotContain(rejectedToken);
        }
    }

    private static String signedToken(
            Map<String, String> claims, Instant issuedAt, Instant expiration, String secret) {
        return signedToken(claims, issuedAt, expiration, secret, "HS256", "HmacSHA256");
    }

    private static String signedToken(
            Map<String, String> claims,
            Instant issuedAt,
            Instant expiration,
            String secret,
            String algorithm,
            String macAlgorithm) {
        var header = base64Url("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}");
        var payload = new StringBuilder("{");
        claims.forEach((name, value) -> {
            if (payload.length() > 1) {
                payload.append(',');
            }
            payload.append('"').append(name).append("\":\"").append(value).append('"');
        });
        if (issuedAt != null) {
            appendSeparator(payload);
            payload.append("\"iat\":").append(issuedAt.getEpochSecond());
        }
        if (expiration != null) {
            appendSeparator(payload);
            payload.append("\"exp\":").append(expiration.getEpochSecond());
        }
        payload.append('}');

        var encodedPayload = base64Url(payload.toString());
        var signingInput = header + "." + encodedPayload;
        return signingInput + "." + base64Url(hmac(signingInput, secret, macAlgorithm));
    }

    private static void appendSeparator(StringBuilder payload) {
        if (payload.length() > 1) {
            payload.append(',');
        }
    }

    private static String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] hmac(String value, String secret, String algorithm) {
        try {
            var mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new AssertionError("Test token signing failed", exception);
        }
    }

    private static final class RecordingService {

        private final String serviceName;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicReference<String> lastPath = new AtomicReference<>();
        private final AtomicReference<com.sun.net.httpserver.Headers> lastHeaders = new AtomicReference<>();
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
            lastPath.set(exchange.getRequestURI().getPath());
            lastHeaders.set(copyHeaders(exchange.getRequestHeaders()));
            var responseBody = "{\"service\":\"" + serviceName + "\"}";
            var responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(responseBytes);
            }
        }

        private static com.sun.net.httpserver.Headers copyHeaders(com.sun.net.httpserver.Headers headers) {
            var copy = new com.sun.net.httpserver.Headers();
            headers.forEach((name, values) -> copy.put(name, List.copyOf(values)));
            return copy;
        }

        private int requests() {
            return requests.get();
        }

        private String lastPath() {
            return lastPath.get();
        }

        private com.sun.net.httpserver.Headers lastHeaders() {
            return lastHeaders.get();
        }

        private void reset() {
            requests.set(0);
            lastPath.set(null);
            lastHeaders.set(new com.sun.net.httpserver.Headers());
        }

        private void stop() {
            if (server != null) {
                server.stop(0);
            }
        }
    }
}
