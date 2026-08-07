package com.suaposta.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayApplicationContextTest {

    @Test
    void should_load_application_context_when_gateway_is_started() {
        try (var context = ApplicationTestSupport.startApplication()) {
            assertThat(context.isActive()).isTrue();
        }
    }
}
