package com.suaposta.betting.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.messaging.contract.EventEnvelope;
import com.suaposta.messaging.contract.MessagingConstants;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Component;

@Component
public final class RabbitBetEventPublisher implements BetEventPublisher {

    private final RabbitOperations rabbitOperations;
    private final ObjectMapper objectMapper;

    public RabbitBetEventPublisher(RabbitOperations rabbitOperations, ObjectMapper objectMapper) {
        this.rabbitOperations = rabbitOperations;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(EventEnvelope envelope, String routingKey) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitOperations.send(
                MessagingConstants.BETTING_EVENTS_EXCHANGE,
                routingKey,
                new Message(serialize(envelope), properties));
    }

    private byte[] serialize(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize betting event", exception);
        }
    }
}
