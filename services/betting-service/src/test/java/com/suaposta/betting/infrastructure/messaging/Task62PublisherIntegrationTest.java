package com.suaposta.betting.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.suaposta.betting.BettingServiceApplication;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.messaging.contract.BetCreatedPayload;
import com.suaposta.messaging.contract.BetSettledPayload;
import com.suaposta.messaging.contract.BetStatus;
import com.suaposta.messaging.contract.BetUpdatedPayload;
import com.suaposta.messaging.contract.EventEnvelope;
import com.suaposta.messaging.contract.EventType;
import com.suaposta.messaging.contract.MessagingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Task62PublisherIntegrationTest {

    private static final String EXCHANGE = MessagingConstants.BETTING_EVENTS_EXCHANGE;
    private static final String QUEUE = MessagingConstants.ANALYTICS_BETTING_EVENTS_QUEUE;
    private static final UUID BET_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-21T22:00:00Z");
    private static final Instant PLACED_AT = Instant.parse("2026-07-21T20:30:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-21T21:20:00Z");
    private static final Instant SETTLED_AT = Instant.parse("2026-07-21T22:00:00Z");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13.7-management"));

    private static Connection rawConnection;
    private static ConnectionFactory rabbitConnectionFactory;
    private static ConfigurableApplicationContext applicationContext;
    private static BetEventPublisher publisher;

    @BeforeAll
    static void startRabbitMqAndPublisher() throws Exception {
        rabbitConnectionFactory = new ConnectionFactory();
        rabbitConnectionFactory.setHost(RABBIT.getHost());
        rabbitConnectionFactory.setPort(RABBIT.getAmqpPort());
        rabbitConnectionFactory.setUsername(RABBIT.getAdminUsername());
        rabbitConnectionFactory.setPassword(RABBIT.getAdminPassword());
        rawConnection = rabbitConnectionFactory.newConnection("task-6.2-publisher-test");

        declareDocumentedTopology();

        applicationContext = new SpringApplicationBuilder(BettingServiceApplication.class)
                .properties(
                        "server.port=0",
                        "spring.rabbitmq.host=" + RABBIT.getHost(),
                        "spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                        "spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                        "spring.rabbitmq.password=" + RABBIT.getAdminPassword())
                .run();
        publisher = applicationContext.getBean(BetEventPublisher.class);
    }

    @AfterAll
    static void stopRabbitMqAndPublisher() throws Exception {
        if (applicationContext != null) {
            applicationContext.close();
        }
        if (rawConnection != null) {
            rawConnection.close();
        }
    }

    @BeforeEach
    void purgeQueue() throws Exception {
        try (var channel = rawConnection.createChannel()) {
            channel.queuePurge(QUEUE);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentedEvents")
    void should_publish_each_documented_event_to_the_exchange_with_its_routing_key(
            String scenario, EventType eventType, String routingKey, Object payload) throws Exception {
        var envelope = new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                OCCURRED_AT,
                MessagingConstants.VERSION_ONE,
                MessagingConstants.BETTING_SERVICE_PRODUCER,
                payload);

        publisher.publish(envelope, routingKey);

        try (var channel = rawConnection.createChannel()) {
            var delivery = channel.basicGet(QUEUE, true);
            assertThat(delivery).as("published %s must reach the Analytics queue", scenario).isNotNull();
            assertThat(delivery.getEnvelope().getExchange()).isEqualTo(EXCHANGE);
            assertThat(delivery.getEnvelope().getRoutingKey()).isEqualTo(routingKey);

            var json = JSON.readTree(delivery.getBody());
            assertThat(json.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder(
                            "eventId", "eventType", "occurredAt", "version", "producer", "payload");
            assertThat(UUID.fromString(json.path("eventId").asText()))
                    .isEqualTo(envelope.eventId());
            assertThat(json.path("eventType").asText()).isEqualTo(eventType.name());
            assertThat(Instant.parse(json.path("occurredAt").asText())).isEqualTo(OCCURRED_AT);
            assertThat(json.path("version").asInt()).isEqualTo(MessagingConstants.VERSION_ONE);
            assertThat(json.path("producer").asText())
                    .isEqualTo(MessagingConstants.BETTING_SERVICE_PRODUCER);
            assertThat(json.path("payload").isObject()).isTrue();
        }
    }

    @Test
    void should_expose_real_serialization_failure_without_publishing_a_fallback_message() {
        var cyclicPayload = new HashMap<String, Object>();
        cyclicPayload.put("self", cyclicPayload);
        var envelope = new EventEnvelope(
                UUID.randomUUID(),
                EventType.BET_CREATED,
                OCCURRED_AT,
                MessagingConstants.VERSION_ONE,
                MessagingConstants.BETTING_SERVICE_PRODUCER,
                cyclicPayload);

        assertThatThrownBy(() -> publisher.publish(
                envelope, MessagingConstants.BET_CREATED_ROUTING_KEY))
                .isInstanceOf(Exception.class)
                .hasRootCauseInstanceOf(JsonProcessingException.class);

        try (var channel = rawConnection.createChannel()) {
            assertThat(channel.basicGet(QUEUE, true))
                    .as("a serialization failure must not send a valid or fallback message")
                    .isNull();
        } catch (Exception exception) {
            throw new AssertionError("could not inspect the queue after serialization failure", exception);
        }
    }

    static Stream<Arguments> documentedEvents() {
        return Stream.of(
                Arguments.of(
                        "BET_CREATED",
                        EventType.BET_CREATED,
                        MessagingConstants.BET_CREATED_ROUTING_KEY,
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
                                BetStatus.PENDING,
                                PLACED_AT)),
                Arguments.of(
                        "BET_UPDATED",
                        EventType.BET_UPDATED,
                        MessagingConstants.BET_UPDATED_ROUTING_KEY,
                        new BetUpdatedPayload(
                                BET_ID,
                                USER_ID,
                                "TENNIS",
                                "Updated League",
                                "Player A",
                                "Player B",
                                "MATCH_WINNER",
                                "Player A",
                                new BigDecimal("2.1256"),
                                new BigDecimal("120.13"),
                                BetStatus.PENDING,
                                PLACED_AT,
                                UPDATED_AT)),
                Arguments.of(
                        "BET_SETTLED",
                        EventType.BET_SETTLED,
                        MessagingConstants.BET_SETTLED_ROUTING_KEY,
                        new BetSettledPayload(
                                BET_ID,
                                USER_ID,
                                BetStatus.WON,
                                new BigDecimal("2.1256"),
                                new BigDecimal("120.13"),
                                new BigDecimal("11.11"),
                                new BigDecimal("131.24"),
                                SETTLED_AT)));
    }

    private static void declareDocumentedTopology() throws Exception {
        try (var channel = rawConnection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.TOPIC, true);
            channel.queueDeclare(QUEUE, true, false, false, null);
            channel.queueBind(QUEUE, EXCHANGE, MessagingConstants.BET_CREATED_ROUTING_KEY);
            channel.queueBind(QUEUE, EXCHANGE, MessagingConstants.BET_UPDATED_ROUTING_KEY);
            channel.queueBind(QUEUE, EXCHANGE, MessagingConstants.BET_SETTLED_ROUTING_KEY);
        }
    }
}
