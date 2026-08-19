package com.suaposta.analytics.infrastructure.messaging;

import com.suaposta.analytics.application.service.BettingEventProcessor;
import com.suaposta.messaging.contract.MessagingConstants;
import java.io.IOException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"spring.datasource.url", "spring.rabbitmq.host"})
public final class RabbitBettingEventListener {

    private final BettingEventMessageDecoder decoder;
    private final BettingEventProcessor processor;

    public RabbitBettingEventListener(BettingEventMessageDecoder decoder, BettingEventProcessor processor) {
        this.decoder = decoder;
        this.processor = processor;
    }

    @RabbitListener(queues = MessagingConstants.ANALYTICS_BETTING_EVENTS_QUEUE)
    public void consume(byte[] message) throws IOException {
        processor.process(decoder.decode(message));
    }
}
