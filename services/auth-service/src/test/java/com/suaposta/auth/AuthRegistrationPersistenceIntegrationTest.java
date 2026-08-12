package com.suaposta.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class AuthRegistrationPersistenceIntegrationTest {

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
    void should_persist_email_after_normalizing_whitespace_and_case() throws Exception {
        var normalizedEmail = AuthRegistrationTestSupport.uniqueEmail();
        var input = new AuthRegistrationTestSupport.RegistrationInput(
                "Samuel Gomes",
                "  " + normalizedEmail.toUpperCase(Locale.ROOT) + "  ",
                "StrongPassword123");

        var response = AuthRegistrationTestSupport.register(context, input);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(AuthRegistrationDatabaseAssertions.countExactText(normalizedEmail)).isPositive();
        assertThat(AuthRegistrationDatabaseAssertions.countExactText(input.email())).isZero();
    }

    @Test
    void should_persist_a_bcrypt_hash_that_verifies_the_raw_password_without_persisting_the_raw_password()
            throws Exception {
        var input = AuthRegistrationTestSupport.validInput();

        var response = AuthRegistrationTestSupport.register(context, input);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(AuthRegistrationDatabaseAssertions.containsText(input.password())).isFalse();
        assertThat(AuthRegistrationDatabaseAssertions.findBcryptHashMatching(input.password()))
                .as("the submitted password must be represented by a persisted BCrypt hash")
                .isPresent();
    }

    @Test
    void should_not_create_a_second_persisted_user_for_duplicate_email_after_normalization() throws Exception {
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

    @Test
    void should_not_persist_a_registration_with_a_short_password() throws Exception {
        var input = new AuthRegistrationTestSupport.RegistrationInput(
                "Samuel Gomes", AuthRegistrationTestSupport.uniqueEmail(), "1234567");
        var rowsBefore = AuthRegistrationDatabaseAssertions.countRowsInBaseTables();

        var response = AuthRegistrationTestSupport.register(context, input);

        AuthRegistrationTestSupport.assertValidationResponse(response, "password", input.password());
        assertThat(AuthRegistrationDatabaseAssertions.countRowsInBaseTables()).isEqualTo(rowsBefore);
        assertThat(AuthRegistrationDatabaseAssertions.containsText(input.email())).isFalse();
    }
}
