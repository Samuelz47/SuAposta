package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BettingApplicationContextTest {

    @Test
    void should_load_application_context_when_betting_service_is_started() {
        try (var context = ApplicationTestSupport.startApplication()) {
            assertThat(context.isActive()).isTrue();
        }
    }
}
