package com.suaposta.analytics.application.model;

import com.suaposta.messaging.contract.EventType;
import java.time.Instant;
import java.util.UUID;

public record ProcessedEvent(UUID eventId, EventType eventType, Instant processedAt) {
}
