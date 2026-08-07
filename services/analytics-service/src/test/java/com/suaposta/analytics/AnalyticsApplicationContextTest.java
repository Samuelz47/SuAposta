package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalyticsApplicationContextTest {

    @Test
    void should_load_application_context_when_analytics_service_is_started() {
        try (var context = ApplicationTestSupport.startApplication()) {
            assertThat(context.isActive()).isTrue();
        }
    }
}
