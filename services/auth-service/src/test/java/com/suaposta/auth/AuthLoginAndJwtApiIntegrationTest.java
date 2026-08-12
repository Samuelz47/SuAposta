package com.suaposta.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;

class AuthLoginAndJwtApiIntegrationTest {

    private static final String LOGIN_PATH = "/auth/login";
    private static final String JWT_SECRET = "task-4-2-test-secret-" + UUID.randomUUID();
    private static final String DIFFERENT_SECRET = "task-4-2-different-secret-" + UUID.randomUUID();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static ConfigurableApplicationContext context;
    private static String previousJwtSecret;
    private static AuthRegistrationTestSupport.RegistrationInput registeredUser;
    private static UUID registeredUserId;
    private static String persistedPasswordHash;

    @BeforeAll
    static void startApplicationAndRegisterIsolatedUser() throws Exception {
        previousJwtSecret = System.getProperty("JWT_SECRET");
        System.setProperty("JWT_SECRET", JWT_SECRET);
        context = AuthRegistrationTestSupport.startApplication();

        registeredUser = AuthRegistrationTestSupport.validInput();
        var registration = AuthRegistrationTestSupport.register(context, registeredUser);
        assertThat(registration.statusCode()).isEqualTo(201);
        registeredUserId = UUID.fromString(AuthRegistrationTestSupport.json(registration).get("id").asText());

        persistedPasswordHash = findPersistedPasswordHash(registeredUser.email());
        assertThat(persistedPasswordHash)
                .as("the isolated login user must have a persisted BCrypt hash")
                .isNotBlank();
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
        if (previousJwtSecret == null) {
            System.clearProperty("JWT_SECRET");
        } else {
            System.setProperty("JWT_SECRET", previousJwtSecret);
        }
    }

    @Test
    void should_return_200_and_exact_documented_success_response_for_valid_credentials() throws Exception {
        var response = login(registeredUser.email(), registeredUser.password());

        assertThat(response.statusCode()).isEqualTo(200);

        var body = json(response);
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "accessToken", "tokenType", "expiresIn", "user");
        assertThat(body.get("accessToken").isTextual()).isTrue();
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").isIntegralNumber()).isTrue();
        assertThat(body.get("expiresIn").asLong()).isEqualTo(3600);

        var user = body.get("user");
        assertThat(user.isObject()).isTrue();
        assertThat(fieldNames(user)).containsExactlyInAnyOrder("id", "name", "email");
        assertThat(UUID.fromString(user.get("id").asText())).isEqualTo(registeredUserId);
        assertThat(user.get("name").asText()).isEqualTo(registeredUser.name());
        assertThat(user.get("email").asText()).isEqualTo(registeredUser.email());
        assertThat(body.findValue("password")).isNull();
        assertThat(body.findValue("passwordHash")).isNull();
        assertThat(response.body()).doesNotContain(registeredUser.password(), persistedPasswordHash, JWT_SECRET);
    }

    @Test
    void should_normalize_email_before_lookup_and_return_the_registered_normalized_email() throws Exception {
        var response = login(
                "  " + registeredUser.email().toUpperCase(Locale.ROOT) + "  ",
                registeredUser.password());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("user").get("email").asText())
                .isEqualTo(registeredUser.email());
    }

    @Test
    void should_issue_a_jwt_signed_with_hs256_using_the_configured_jwt_secret() throws Exception {
        var token = successfulToken();
        var parts = jwtParts(token);
        var header = parts.header();

        assertThat(header.get("alg").asText()).isEqualTo("HS256");
        assertThat(verifyHs256Signature(token, JWT_SECRET)).isTrue();
        assertThat(verifyHs256Signature(token, DIFFERENT_SECRET)).isFalse();
    }

    @Test
    void should_issue_a_gateway_compatible_jwt_with_required_claims_and_exact_lifetime() throws Exception {
        var token = successfulToken();
        var parts = jwtParts(token);
        var claims = parts.claims();

        assertThat(parts.header().get("alg").asText()).isEqualTo("HS256");
        assertThat(verifyHs256Signature(token, JWT_SECRET)).isTrue();
        assertThat(claims.get("sub").asText()).isEqualTo(registeredUserId.toString());
        assertThat(UUID.fromString(claims.get("sub").asText())).isEqualTo(registeredUserId);
        assertThat(claims.get("iat").isIntegralNumber()).isTrue();
        assertThat(claims.get("exp").isIntegralNumber()).isTrue();
        assertThat(claims.get("iat").canConvertToLong()).isTrue();
        assertThat(claims.get("exp").canConvertToLong()).isTrue();
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(3600);
        assertThat(claims.get("exp").asLong()).isGreaterThan(claims.get("iat").asLong());

        assertThat(claims.findValue("password")).isNull();
        assertThat(claims.findValue("passwordHash")).isNull();
        assertThat(claims.findValue("credential")).isNull();
        assertThat(claims.toString()).doesNotContain(
                registeredUser.password(), persistedPasswordHash, JWT_SECRET);
    }

    @Test
    void should_validate_the_submitted_password_against_the_persisted_bcrypt_hash() throws Exception {
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

        assertThat(persistedPasswordHash).startsWith("$2");
        assertThat(persistedPasswordHash).isNotEqualTo(registeredUser.password());
        assertThat(encoder.matches(registeredUser.password(), persistedPasswordHash)).isTrue();

        var validResponse = login(registeredUser.email(), registeredUser.password());
        assertThat(validResponse.statusCode()).isEqualTo(200);

        var hashAsSubmittedPassword = login(registeredUser.email(), persistedPasswordHash);
        assertUnauthorizedWithoutSensitiveData(hashAsSubmittedPassword, persistedPasswordHash);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLoginRequests")
    void should_return_documented_validation_error_without_issuing_a_token_for_invalid_request_shape(
            String scenario, ObjectNode request, String expectedField) throws Exception {
        var response = login(request);

        assertThat(response.statusCode()).isEqualTo(400);
        assertValidationResponse(response, expectedField);
        assertThat(response.body()).doesNotContain("accessToken", JWT_SECRET, persistedPasswordHash);
    }

    @Test
    void should_treat_a_non_blank_short_password_as_a_credential_attempt() throws Exception {
        var response = login(registeredUser.email(), "x");

        assertUnauthorizedWithoutSensitiveData(response, "x");
    }

    @Test
    void should_return_401_without_a_token_for_an_unknown_email() throws Exception {
        var response = login("unknown-" + UUID.randomUUID() + "@example.com", registeredUser.password());

        assertUnauthorizedWithoutSensitiveData(response, registeredUser.password());
    }

    @Test
    void should_return_401_without_a_token_for_an_incorrect_password() throws Exception {
        var response = login(registeredUser.email(), "WrongPassword123");

        assertUnauthorizedWithoutSensitiveData(response, "WrongPassword123");
    }

    @Test
    void should_return_the_same_external_error_for_unknown_email_and_incorrect_password() throws Exception {
        var unknownEmailResponse = login("unknown-" + UUID.randomUUID() + "@example.com", registeredUser.password());
        var incorrectPasswordResponse = login(registeredUser.email(), "WrongPassword123");

        assertThat(unknownEmailResponse.statusCode()).isEqualTo(401);
        assertThat(incorrectPasswordResponse.statusCode()).isEqualTo(401);
        assertThat(withoutTimestamp(json(unknownEmailResponse)))
                .isEqualTo(withoutTimestamp(json(incorrectPasswordResponse)));
        assertThat(json(unknownEmailResponse).get("message").asText())
                .doesNotContainIgnoringCase("email", "password");
        assertThat(json(incorrectPasswordResponse).get("message").asText())
                .doesNotContainIgnoringCase("email", "password");
    }

    private static Stream<Arguments> invalidLoginRequests() {
        var validEmail = registeredUser.email();
        var validPassword = registeredUser.password();
        return Stream.of(
                Arguments.of("missing email", request(null, validPassword), "email"),
                Arguments.of("blank email", request("   ", validPassword), "email"),
                Arguments.of("malformed email", request("not-an-email", validPassword), "email"),
                Arguments.of("missing password", request(validEmail, null), "password"),
                Arguments.of("blank password", request(validEmail, "   "), "password"));
    }

    private static HttpResponse<String> login(String email, String password) throws Exception {
        return login(request(email, password));
    }

    private static String successfulToken() throws Exception {
        var response = login(registeredUser.email(), registeredUser.password());
        assertThat(response.statusCode()).isEqualTo(200);
        var token = json(response).get("accessToken");
        assertThat(token).isNotNull();
        assertThat(token.isTextual()).isTrue();
        return token.asText();
    }

    private static HttpResponse<String> login(ObjectNode body) throws Exception {
        var webContext = (org.springframework.boot.web.context.WebServerApplicationContext) context;
        var port = webContext.getWebServer().getPort();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + LOGIN_PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static ObjectNode request(String email, String password) {
        var body = AuthRegistrationTestSupport.JSON.createObjectNode();
        if (email != null) {
            body.put("email", email);
        }
        if (password != null) {
            body.put("password", password);
        }
        return body;
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception {
        return AuthRegistrationTestSupport.JSON.readTree(response.body());
    }

    private static void assertValidationResponse(HttpResponse<String> response, String expectedField)
            throws Exception {
        var body = json(response);
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "timestamp", "status", "error", "message", "path", "fieldErrors");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(Instant.parse(body.get("timestamp").asText())).isNotNull();
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("error").asText()).isEqualTo("Validation Error");
        assertThat(body.get("message").asText()).isEqualTo("Invalid request fields");
        assertThat(body.get("path").asText()).isEqualTo(LOGIN_PATH);
        assertThat(body.get("fieldErrors").isArray()).isTrue();
        var containsExpectedField = false;
        for (JsonNode fieldError : body.get("fieldErrors")) {
            assertThat(fieldErrorNames(fieldError)).containsExactlyInAnyOrder("field", "message");
            assertThat(fieldError.get("message").asText()).isNotBlank();
            containsExpectedField |= fieldError.get("field").asText().equals(expectedField);
        }
        assertThat(containsExpectedField).isTrue();
        assertThat(body.findValue("accessToken")).isNull();
        assertThat(body.findValue("password")).isNull();
        assertThat(body.findValue("passwordHash")).isNull();
    }

    private static void assertUnauthorizedWithoutSensitiveData(
            HttpResponse<String> response, String submittedPassword) throws Exception {
        assertThat(response.statusCode()).isEqualTo(401);
        var body = json(response);
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "timestamp", "status", "error", "message", "path");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(Instant.parse(body.get("timestamp").asText())).isNotNull();
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("path").asText()).isEqualTo(LOGIN_PATH);
        assertThat(body.findValue("accessToken")).isNull();
        assertThat(body.findValue("password")).isNull();
        assertThat(body.findValue("passwordHash")).isNull();

        var lowerBody = response.body().toLowerCase(Locale.ROOT);
        assertThat(lowerBody)
                .doesNotContain(
                        "email",
                        "password",
                        "passwordhash",
                        "stacktrace",
                        "stack trace",
                        "database",
                        "org.springframework",
                        "java.lang",
                        JWT_SECRET.toLowerCase(Locale.ROOT));
        assertThat(response.body()).doesNotContain(submittedPassword, persistedPasswordHash);
    }

    private static JsonNode withoutTimestamp(JsonNode body) {
        var copy = (ObjectNode) body.deepCopy();
        copy.remove("timestamp");
        return copy;
    }

    private static Set<String> fieldNames(JsonNode node) {
        var names = new HashSet<String>();
        Iterator<String> fields = node.fieldNames();
        fields.forEachRemaining(names::add);
        return names;
    }

    private static Set<String> fieldErrorNames(JsonNode node) {
        return fieldNames(node);
    }

    private static JwtParts jwtParts(String token) throws Exception {
        var segments = token.split("\\.", -1);
        assertThat(segments).as("accessToken must have JWT header, payload, and signature").hasSize(3);
        return new JwtParts(
                AuthRegistrationTestSupport.JSON.readTree(decode(segments[0])),
                AuthRegistrationTestSupport.JSON.readTree(decode(segments[1])),
                segments[0] + "." + segments[1],
                Base64.getUrlDecoder().decode(segments[2]));
    }

    private static boolean verifyHs256Signature(String token, String secret) throws Exception {
        var parts = jwtParts(token);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.security.MessageDigest.isEqual(
                mac.doFinal(parts.signingInput().getBytes(StandardCharsets.UTF_8)),
                parts.signature());
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String findPersistedPasswordHash(String email) throws Exception {
        try (var connection = DriverManager.getConnection(
                databaseUrl(), databaseUser(), databasePassword());
                var statement = connection.prepareStatement(
                        "SELECT password_hash FROM users WHERE email = ?")) {
            statement.setString(1, email);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString("password_hash");
            }
        }
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

    private record JwtParts(JsonNode header, JsonNode claims, String signingInput, byte[] signature) {
    }
}
