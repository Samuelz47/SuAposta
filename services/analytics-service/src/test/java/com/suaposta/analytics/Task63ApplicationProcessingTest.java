package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.suaposta.messaging.contract.BetCreatedPayload;
import com.suaposta.messaging.contract.EventEnvelope;
import com.suaposta.messaging.contract.EventType;
import com.suaposta.messaging.contract.MessagingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Task63ApplicationProcessingTest {

    private static final UUID BET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-18T10:00:00Z");

    @Test
    void should_process_a_valid_created_event_once_through_the_application_boundary() {
        var processor = Task63TestSupport.requireApplicationProcessor();
        var dependencies = Task63TestSupport.constructProcessor(processor);
        var envelope = createdEvent(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        processor.invoke(dependencies.target(), envelope);
        processor.invoke(dependencies.target(), envelope);

        assertThat(dependencies.projectionCount()).isEqualTo(1);
        assertThat(dependencies.processedCount()).isEqualTo(1);
        assertThat(dependencies.projectionWriteCount()).isEqualTo(1);
        assertThat(dependencies.processedWriteCount()).isEqualTo(1);
    }

    @Test
    void should_reject_a_created_event_with_an_invalid_payload_before_persistence() {
        var processor = Task63TestSupport.requireApplicationProcessor();
        var dependencies = Task63TestSupport.constructProcessor(processor);
        var invalidPayload = new BetCreatedPayload(
                null, USER_ID, "FOOTBALL", "League", "Home", "Away", "MARKET", "Selection",
                new BigDecimal("2.1256"), new BigDecimal("120.13"),
                com.suaposta.messaging.contract.BetStatus.PENDING, OCCURRED_AT);
        var envelope = new EventEnvelope(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), EventType.BET_CREATED,
                OCCURRED_AT, MessagingConstants.VERSION_ONE,
                MessagingConstants.BETTING_SERVICE_PRODUCER, invalidPayload);

        assertThatThrownBy(() -> processor.invoke(dependencies.target(), envelope))
                .isInstanceOf(RuntimeException.class);
        assertThat(dependencies.projectionCount()).isZero();
        assertThat(dependencies.processedCount()).isZero();
    }

    private static EventEnvelope createdEvent(UUID eventId) {
        return new EventEnvelope(
                eventId,
                EventType.BET_CREATED,
                OCCURRED_AT,
                MessagingConstants.VERSION_ONE,
                MessagingConstants.BETTING_SERVICE_PRODUCER,
                new BetCreatedPayload(
                        BET_ID,
                        USER_ID,
                        "FOOTBALL",
                        "League",
                        "Home",
                        "Away",
                        "MATCH_RESULT",
                        "Home",
                        new BigDecimal("2.1256"),
                        new BigDecimal("120.13"),
                        com.suaposta.messaging.contract.BetStatus.PENDING,
                        Instant.parse("2026-08-18T09:30:00Z")));
    }
}
