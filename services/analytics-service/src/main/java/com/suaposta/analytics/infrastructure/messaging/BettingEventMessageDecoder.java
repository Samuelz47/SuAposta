package com.suaposta.analytics.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suaposta.messaging.contract.BetCreatedPayload;
import com.suaposta.messaging.contract.BetSettledPayload;
import com.suaposta.messaging.contract.BetUpdatedPayload;
import com.suaposta.messaging.contract.EventEnvelope;
import com.suaposta.messaging.contract.EventType;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class BettingEventMessageDecoder {

    private final ObjectMapper objectMapper;

    public BettingEventMessageDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    EventEnvelope decode(byte[] message) throws IOException {
        var root = objectMapper.readTree(message);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Betting event must be a JSON object");
        }
        var eventType = EventType.valueOf(requiredText(root, "eventType"));
        var payloadNode = root.get("payload");
        if (payloadNode == null || payloadNode.isNull() || !payloadNode.isObject()) {
            throw new IllegalArgumentException("Betting event payload must be a JSON object");
        }
        var payload = switch (eventType) {
            case BET_CREATED -> objectMapper.treeToValue(payloadNode, BetCreatedPayload.class);
            case BET_UPDATED -> objectMapper.treeToValue(payloadNode, BetUpdatedPayload.class);
            case BET_SETTLED -> objectMapper.treeToValue(payloadNode, BetSettledPayload.class);
        };
        return new EventEnvelope(
                UUID.fromString(requiredText(root, "eventId")),
                eventType,
                Instant.parse(requiredText(root, "occurredAt")),
                requiredInteger(root, "version"),
                requiredText(root, "producer"),
                payload);
    }

    private static String requiredText(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(field + " must be a JSON string");
        }
        return value.textValue();
    }

    private static int requiredInteger(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " must be a JSON integer");
        }
        return value.intValue();
    }
}
