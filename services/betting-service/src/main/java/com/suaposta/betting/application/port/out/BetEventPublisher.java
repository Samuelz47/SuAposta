package com.suaposta.betting.application.port.out;

import com.suaposta.messaging.contract.EventEnvelope;

public interface BetEventPublisher {

    void publish(EventEnvelope envelope, String routingKey);
}
