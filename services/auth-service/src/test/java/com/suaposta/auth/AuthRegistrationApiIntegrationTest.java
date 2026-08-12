package com.suaposta.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;

class AuthRegistrationApiIntegrationTest {

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startApplication() {
        context = AuthRegistrationTestSupport.startApplication();
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void should_return_created_with_exact_success_contract_when_registration_is_valid() throws Exception {
        var input = AuthRegistrationTestSupport.validInput();
        var response = AuthRegistrationTestSupport.register(context, input);

        AuthRegistrationTestSupport.assertSuccessResponse(response, input, input.email());
    }

    @Test
    void should_normalize_email_before_persisting_and_comparing_it() throws Exception {
        var normalizedEmail = AuthRegistrationTestSupport.uniqueEmail();
        var input = new AuthRegistrationTestSupport.RegistrationInput(
                "Samuel Gomes",
                "  " + normalizedEmail.toUpperCase(Locale.ROOT) + "  ",
                "StrongPassword123");

        var response = AuthRegistrationTestSupport.register(context, input);

        assertThat(response.statusCode()).isEqualTo(201);

        var duplicate = new AuthRegistrationTestSupport.RegistrationInput(
                "Another User",
                normalizedEmail.toUpperCase(Locale.ROOT),
                "AnotherPassword123");
        var duplicateResponse = AuthRegistrationTestSupport.register(context, duplicate);

        AuthRegistrationTestSupport.assertConflictResponse(duplicateResponse, duplicate.password());
    }

    @Test
    void should_accept_exactly_eight_password_characters_without_additional_complexity_rules() throws Exception {
        var input = new AuthRegistrationTestSupport.RegistrationInput(
                "Samuel Gomes", AuthRegistrationTestSupport.uniqueEmail(), "abcdefgh");

        var response = AuthRegistrationTestSupport.register(context, input);

        AuthRegistrationTestSupport.assertSuccessResponse(response, input, input.email());
    }

    @Test
    void should_return_conflict_and_not_create_another_user_for_duplicate_normalized_email() throws Exception {
        var normalizedEmail = AuthRegistrationTestSupport.uniqueEmail();
        var first = new AuthRegistrationTestSupport.RegistrationInput(
                "Samuel Gomes", normalizedEmail, "StrongPassword123");
        var duplicate = new AuthRegistrationTestSupport.RegistrationInput(
                "Another User", "  " + normalizedEmail.toUpperCase(Locale.ROOT) + "  ", "AnotherPassword123");

        var firstResponse = AuthRegistrationTestSupport.register(context, first);
        assertThat(firstResponse.statusCode()).isEqualTo(201);
        var persistedBeforeDuplicate = AuthRegistrationDatabaseAssertions.countExactText(normalizedEmail);
        assertThat(persistedBeforeDuplicate).isPositive();

        var duplicateResponse = AuthRegistrationTestSupport.register(context, duplicate);

        AuthRegistrationTestSupport.assertConflictResponse(duplicateResponse, duplicate.password());
        assertThat(AuthRegistrationDatabaseAssertions.countExactText(normalizedEmail))
                .isEqualTo(persistedBeforeDuplicate);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrationInputs")
    void should_return_documented_validation_error_and_not_persist_invalid_input(
            String scenario,
            String expectedField,
            AuthRegistrationTestSupport.RegistrationInput input) throws Exception {
        var rowsBefore = AuthRegistrationDatabaseAssertions.countRowsInBaseTables();

        var response = AuthRegistrationTestSupport.register(context, input);

        AuthRegistrationTestSupport.assertValidationResponse(response, expectedField, input.password());
        assertThat(AuthRegistrationDatabaseAssertions.countRowsInBaseTables()).isEqualTo(rowsBefore);
    }

    private static Stream<Arguments> invalidRegistrationInputs() {
        var suffix = UUID.randomUUID();
        return Stream.of(
                Arguments.of(
                        "blank name",
                        "name",
                        new AuthRegistrationTestSupport.RegistrationInput(
                                "   ", "blank-name-" + suffix + "@example.com", "StrongPassword123")),
                Arguments.of(
                        "blank email",
                        "email",
                        new AuthRegistrationTestSupport.RegistrationInput(
                                "Samuel Gomes", "  ", "StrongPassword123")),
                Arguments.of(
                        "malformed email",
                        "email",
                        new AuthRegistrationTestSupport.RegistrationInput(
                                "Samuel Gomes", "not-an-email", "StrongPassword123")),
                Arguments.of(
                        "blank password",
                        "password",
                        new AuthRegistrationTestSupport.RegistrationInput(
                                "Samuel Gomes", "blank-password-" + suffix + "@example.com", "")),
                Arguments.of(
                        "password shorter than eight characters",
                        "password",
                        new AuthRegistrationTestSupport.RegistrationInput(
                                "Samuel Gomes", "short-password-" + suffix + "@example.com", "1234567")));
    }
}
