package com.suaposta.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthApplicationContextTest {

    @Test
    void should_load_application_context_without_external_infrastructure() {
        try (var context = ApplicationTestSupport.startApplication()) {
            assertThat(context.isActive()).isTrue();
        }
    }
}
