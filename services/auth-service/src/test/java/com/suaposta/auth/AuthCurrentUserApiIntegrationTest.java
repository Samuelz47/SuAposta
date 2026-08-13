package com.suaposta.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class AuthCurrentUserApiIntegrationTest {

    private static final String CURRENT_USER_PATH = "/auth/me";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static ConfigurableApplicationContext context;
    private static AuthRegistrationTestSupport.RegistrationInput authenticatedUser;
    private static AuthRegistrationTestSupport.RegistrationInput otherUser;
    private static UUID authenticatedUserId;
    private static UUID otherUserId;

    @BeforeAll
    static void startApplicationAndRegisterIsolatedUsers() throws Exception {
        context = AuthRegistrationTestSupport.startApplication();

        authenticatedUser = AuthRegistrationTestSupport.validInput();
        otherUser = new AuthRegistrationTestSupport.RegistrationInput(
                "Other Auth Test User",
                AuthRegistrationTestSupport.uniqueEmail(),
                "AnotherStrongPassword123");

        var authenticatedRegistration = AuthRegistrationTestSupport.register(context, authenticatedUser);
        var otherRegistration = AuthRegistrationTestSupport.register(context, otherUser);
        assertThat(authenticatedRegistration.statusCode()).isEqualTo(201);
        assertThat(otherRegistration.statusCode()).isEqualTo(201);

        authenticatedUserId = UUID.fromString(
                AuthRegistrationTestSupport.json(authenticatedRegistration).get("id").asText());
        otherUserId = UUID.fromString(
                AuthRegistrationTestSupport.json(otherRegistration).get("id").asText());
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void should_return_the_authenticated_user_with_exact_safe_contract_without_bearer_authorization()
            throws Exception {
        var response = get(CURRENT_USER_PATH, authenticatedUserId.toString(), null, null);

        assertCurrentUserResponse(response);
    }

    @Test
    void should_ignore_the_bearer_header_as_an_identity_source_when_x_user_id_is_valid() throws Exception {
        var response = get(
                CURRENT_USER_PATH,
                authenticatedUserId.toString(),
                "Bearer not-a-jwt",
                null);

        assertCurrentUserResponse(response);
    }

    @Test
    void should_use_x_user_id_instead_of_a_query_parameter_to_choose_the_identity() throws Exception {
        var response = get(
                CURRENT_USER_PATH + "?userId=" + otherUserId + "&id=" + otherUserId,
                authenticatedUserId.toString(),
                null,
                null);

        assertCurrentUserResponse(response);
    }

    @Test
    void should_use_x_user_id_instead_of_a_request_body_to_choose_the_identity() throws Exception {
        var body = AuthRegistrationTestSupport.JSON.createObjectNode()
                .put("userId", otherUserId.toString())
                .put("id", otherUserId.toString());

        var response = get(
                CURRENT_USER_PATH,
                authenticatedUserId.toString(),
                null,
                body.toString());

        assertCurrentUserResponse(response);
    }

    @Test
    void should_not_allow_a_path_identifier_to_replace_the_authenticated_identity() throws Exception {
        var response = get(
                CURRENT_USER_PATH + "/" + otherUserId,
                authenticatedUserId.toString(),
                null,
                null);

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain(otherUser.name(), otherUser.email());
    }

    @Test
    void should_return_401_when_x_user_id_is_missing() throws Exception {
        var response = get(CURRENT_USER_PATH, null, null, null);

        assertUnauthorizedSafe(response);
    }

    @Test
    void should_return_401_when_x_user_id_is_not_a_uuid() throws Exception {
        var response = get(CURRENT_USER_PATH, "not-a-user-uuid", null, null);

        assertUnauthorizedSafe(response);
    }

    @Test
    void should_return_401_when_x_user_id_is_a_valid_uuid_without_a_corresponding_user() throws Exception {
        var response = get(CURRENT_USER_PATH, UUID.randomUUID().toString(), null, null);

        assertUnauthorizedSafe(response);
    }

    @Test
    void should_return_the_same_safe_unauthorized_contract_for_different_nonexistent_users() throws Exception {
        var removedUser = AuthRegistrationTestSupport.validInput();
        var registration = AuthRegistrationTestSupport.register(context, removedUser);
        assertThat(registration.statusCode()).isEqualTo(201);
        var removedUserId = UUID.fromString(
                AuthRegistrationTestSupport.json(registration).get("id").asText());
        deleteUser(removedUserId);

        var removedUserResponse = get(CURRENT_USER_PATH, removedUserId.toString(), null, null);
        var neverExistingUserResponse = get(CURRENT_USER_PATH, UUID.randomUUID().toString(), null, null);

        assertUnauthorizedSafe(removedUserResponse);
        assertUnauthorizedSafe(neverExistingUserResponse);
        assertThat(withoutTimestamp(json(removedUserResponse)))
                .isEqualTo(withoutTimestamp(json(neverExistingUserResponse)));
    }

    private static HttpResponse<String> get(
            String path,
            String userId,
            String authorization,
            String requestBody) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port() + path));
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (requestBody == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .method("GET", HttpRequest.BodyPublishers.ofString(requestBody));
        }
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int port() {
        return ((org.springframework.boot.web.context.WebServerApplicationContext) context)
                .getWebServer()
                .getPort();
    }

    private static void assertCurrentUserResponse(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);

        var body = json(response);
        assertThat(fieldNames(body)).containsExactlyInAnyOrder("id", "name", "email");
        assertThat(UUID.fromString(body.get("id").asText())).isEqualTo(authenticatedUserId);
        assertThat(body.get("name").asText()).isEqualTo(authenticatedUser.name());
        assertThat(body.get("email").asText()).isEqualTo(authenticatedUser.email());
        assertThat(body.findValue("password")).isNull();
        assertThat(body.findValue("passwordHash")).isNull();
        assertThat(response.body().toLowerCase())
                .doesNotContain("accesstoken", "bearer", "jwt", "secret", "credential");
        assertThat(response.body()).doesNotContain(authenticatedUser.password(), otherUser.password());
    }

    private static void assertUnauthorizedSafe(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(401);

        var body = json(response);
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "timestamp", "status", "error", "message", "path");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(Instant.parse(body.get("timestamp").asText())).isNotNull();
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("path").asText()).isEqualTo(CURRENT_USER_PATH);
        assertThat(response.body().toLowerCase())
                .doesNotContain(
                        "password",
                        "passwordhash",
                        "credential",
                        "jwt",
                        "secret",
                        "authorization",
                        "bearer",
                        "stacktrace",
                        "database",
                        "org.springframework",
                        "java.lang",
                        "exception");
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception {
        return AuthRegistrationTestSupport.JSON.readTree(response.body());
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

    private static void deleteUser(UUID userId) throws Exception {
        try (var connection = DriverManager.getConnection(
                setting(
                        "auth.test.db.url",
                        "AUTH_DB_JDBC_URL",
                        "jdbc:postgresql://"
                                + setting("auth.test.db.host", "POSTGRES_HOST", "127.0.0.1")
                                + ":"
                                + setting("auth.test.db.port", "POSTGRES_HOST_PORT", "5432")
                                + "/"
                                + setting("auth.test.db.name", "AUTH_DB_NAME", "suaposta_auth")),
                setting("auth.test.db.user", "AUTH_DB_USER", "suaposta_auth"),
                setting("auth.test.db.password", "AUTH_DB_PASSWORD", "change_me_auth"));
                var statement = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
            statement.setObject(1, userId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
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
