package com.suaposta.analytics.infrastructure.config;

import static com.suaposta.messaging.contract.MessagingConstants.ANALYTICS_BETTING_EVENTS_QUEUE;
import static com.suaposta.messaging.contract.MessagingConstants.BET_CREATED_ROUTING_KEY;
import static com.suaposta.messaging.contract.MessagingConstants.BET_SETTLED_ROUTING_KEY;
import static com.suaposta.messaging.contract.MessagingConstants.BETTING_EVENTS_EXCHANGE;
import static com.suaposta.messaging.contract.MessagingConstants.BET_UPDATED_ROUTING_KEY;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopologyConfiguration {

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.rabbitmq", name = "host")
    ApplicationRunner rabbitTopologyInitializer(RabbitAdmin rabbitAdmin) {
        return arguments -> rabbitAdmin.initialize();
    }

    @Bean
    TopicExchange bettingEventsExchange() {
        return new TopicExchange(BETTING_EVENTS_EXCHANGE);
    }

    @Bean
    Queue analyticsBettingEventsQueue() {
        return new Queue(ANALYTICS_BETTING_EVENTS_QUEUE);
    }

    @Bean
    Binding betCreatedBinding(Queue analyticsBettingEventsQueue, TopicExchange bettingEventsExchange) {
        return BindingBuilder.bind(analyticsBettingEventsQueue)
                .to(bettingEventsExchange)
                .with(BET_CREATED_ROUTING_KEY);
    }

    @Bean
    Binding betUpdatedBinding(Queue analyticsBettingEventsQueue, TopicExchange bettingEventsExchange) {
        return BindingBuilder.bind(analyticsBettingEventsQueue)
                .to(bettingEventsExchange)
                .with(BET_UPDATED_ROUTING_KEY);
    }

    @Bean
    Binding betSettledBinding(Queue analyticsBettingEventsQueue, TopicExchange bettingEventsExchange) {
        return BindingBuilder.bind(analyticsBettingEventsQueue)
                .to(bettingEventsExchange)
                .with(BET_SETTLED_ROUTING_KEY);
    }
}
