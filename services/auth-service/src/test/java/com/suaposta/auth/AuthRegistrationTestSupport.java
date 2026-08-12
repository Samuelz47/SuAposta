package com.suaposta.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

final class AuthRegistrationTestSupport {

    static final String REGISTER_PATH = "/auth/register";
    static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String DB_HOST = setting("auth.test.db.host", "POSTGRES_HOST", "127.0.0.1");
    private static final String DB_PORT = setting("auth.test.db.port", "POSTGRES_HOST_PORT", "5432");
    private static final String DB_NAME = setting("auth.test.db.name", "AUTH_DB_NAME", "suaposta_auth");
    private static final String DB_USER = setting("auth.test.db.user", "AUTH_DB_USER", "suaposta_auth");
    private static final String DB_PASSWORD = setting("auth.test.db.password", "AUTH_DB_PASSWORD", "change_me_auth");
    private static final String JDBC_URL = setting(
            "auth.test.db.url",
            "AUTH_DB_JDBC_URL",
            "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME);

    private AuthRegistrationTestSupport() {
    }

    static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(AuthApplication.class)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=" + JDBC_URL,
                        "spring.datasource.username=" + DB_USER,
                        "spring.datasource.password=" + DB_PASSWORD)
                .run();
    }

    static HttpResponse<String> register(
            ConfigurableApplicationContext context,
            RegistrationInput input) throws Exception {
        var webContext = (WebServerApplicationContext) context;
        var port = webContext.getWebServer().getPort();
        var requestBody = JSON.writeValueAsString(Map.of(
                "name", input.name(),
                "email", input.email(),
                "password", input.password()));
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + REGISTER_PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    static RegistrationInput validInput() {
        var suffix = UUID.randomUUID().toString();
        return new RegistrationInput(
                "Samuel Gomes",
                "auth-test-" + suffix + "@example.com",
                "StrongPassword123");
    }

    static String uniqueEmail() {
        return "auth-test-" + UUID.randomUUID() + "@example.com";
    }

    static JsonNode json(HttpResponse<String> response) throws Exception {
        return JSON.readTree(response.body());
    }

    static void assertSuccessResponse(
            HttpResponse<String> response,
            RegistrationInput input,
            String normalizedEmail) throws Exception {
        assertThat(response.statusCode()).isEqualTo(201);

        var body = json(response);
        assertThat(body.isObject()).isTrue();
        assertThat(fieldNames(body)).containsExactlyInAnyOrder("id", "name", "email", "createdAt");
        assertThat(UUID.fromString(body.get("id").asText())).isNotNull();
        assertThat(body.get("name").asText()).isEqualTo(input.name());
        assertThat(body.get("email").asText()).isEqualTo(normalizedEmail);
        assertThat(Instant.parse(body.get("createdAt").asText())).isNotNull();
        assertThat(body.findValue("password")).isNull();
        assertThat(body.findValue("passwordHash")).isNull();
        assertNoSensitiveData(response, input.password());
    }

    static void assertValidationResponse(
            HttpResponse<String> response,
            String expectedField,
            String rawPassword) throws Exception {
        assertThat(response.statusCode()).isEqualTo(400);

        var body = json(response);
        assertThat(body.isObject()).isTrue();
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "timestamp", "status", "error", "message", "path", "fieldErrors");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(Instant.parse(body.get("timestamp").asText())).isNotNull();
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("error").asText()).isEqualTo("Validation Error");
        assertThat(body.get("message").asText()).isEqualTo("Invalid request fields");
        assertThat(body.get("path").asText()).isEqualTo(REGISTER_PATH);
        assertThat(body.get("fieldErrors").isArray()).isTrue();
        var containsExpectedField = false;
        for (JsonNode fieldError : body.get("fieldErrors")) {
            assertThat(fieldError.isObject()).isTrue();
            assertThat(fieldNames(fieldError)).containsExactlyInAnyOrder("field", "message");
            assertThat(fieldError.get("message").asText()).isNotBlank();
            containsExpectedField |= fieldError.get("field").asText().equals(expectedField);
        }
        assertThat(containsExpectedField).isTrue();
        assertThat(body.findValue("password")).isNull();
        assertThat(body.findValue("passwordHash")).isNull();
        assertNoSensitiveData(response, rawPassword);
    }

    static void assertConflictResponse(
            HttpResponse<String> response,
            String rawPassword) throws Exception {
        assertThat(response.statusCode()).isEqualTo(409);

        var body = json(response);
        assertThat(body.isObject()).isTrue();
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "timestamp", "status", "error", "message", "path");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(Instant.parse(body.get("timestamp").asText())).isNotNull();
        assertThat(body.get("status").asInt()).isEqualTo(409);
        assertThat(body.get("error").asText()).isEqualTo("Conflict");
        assertThat(body.get("message").asText()).isEqualTo("Email already registered");
        assertThat(body.get("path").asText()).isEqualTo(REGISTER_PATH);
        assertThat(body.findValue("password")).isNull();
        assertThat(body.findValue("passwordHash")).isNull();
        assertNoSensitiveData(response, rawPassword);
    }

    private static void assertNoSensitiveData(HttpResponse<String> response, String rawPassword) throws Exception {
        var lowerBody = response.body().toLowerCase(Locale.ROOT);
        assertThat(lowerBody)
                .doesNotContain(
                        "passwordhash",
                        "credential",
                        "stacktrace",
                        "stack trace",
                        "exception",
                        "org.springframework",
                        "java.lang");

        if (!rawPassword.isEmpty()) {
            assertThat(containsSensitiveValue(JSON.readTree(response.body()), rawPassword)).isFalse();
        }
    }

    private static boolean containsSensitiveValue(JsonNode node, String expectedValue) {
        if (node.isValueNode()) {
            return node.asText().contains(expectedValue);
        }

        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                if (containsSensitiveValue(child, expectedValue)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static Set<String> fieldNames(JsonNode node) {
        var names = new HashSet<String>();
        Iterator<String> fields = node.fieldNames();
        fields.forEachRemaining(names::add);
        return names;
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

    record RegistrationInput(String name, String email, String password) {
    }
}
