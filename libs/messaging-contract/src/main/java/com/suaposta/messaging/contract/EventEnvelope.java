package com.suaposta.messaging.contract;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        EventType eventType,
        Instant occurredAt,
        int version,
        String producer,
        Object payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (version != MessagingConstants.VERSION_ONE) {
            throw new IllegalArgumentException("version must be 1");
        }
        if (!MessagingConstants.BETTING_SERVICE_PRODUCER.equals(producer)) {
            throw new IllegalArgumentException("producer must be betting-service");
        }
        if (!(payload instanceof Map<?, ?>
                || payload instanceof BetCreatedPayload
                || payload instanceof BetUpdatedPayload
                || payload instanceof BetSettledPayload)) {
            throw new IllegalArgumentException("payload must be a JSON object");
        }
    }
}
