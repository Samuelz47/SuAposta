package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.suaposta.messaging.contract.BetCreatedPayload;
import com.suaposta.messaging.contract.BetSettledPayload;
import com.suaposta.messaging.contract.BetStatus;
import com.suaposta.messaging.contract.BetUpdatedPayload;
import com.suaposta.messaging.contract.EventEnvelope;
import com.suaposta.messaging.contract.EventType;
import com.suaposta.messaging.contract.MessagingConstants;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@Testcontainers
class Task63RabbitConsumerIntegrationTest {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private static final UUID BET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant CREATED_OCCURRED_AT = Instant.parse("2026-08-18T10:00:00Z");
    private static final Instant PLACED_AT = Instant.parse("2026-08-18T09:30:00Z");
    private static final Instant UPDATED_OCCURRED_AT = Instant.parse("2026-08-18T11:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-18T10:45:00Z");
    private static final Instant SETTLED_OCCURRED_AT = Instant.parse("2026-08-18T12:00:00Z");
    private static final Instant SETTLED_AT = Instant.parse("2026-08-18T11:55:00Z");
    private static final UUID PROJECTION_FAILURE_BET_ID =
            UUID.fromString("16161616-1616-1616-1616-161616161616");
    private static final UUID PROCESSED_EVENT_FAILURE_ID =
            UUID.fromString("17171717-1717-1717-1717-171717171717");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13.7-management"));

    private static org.springframework.context.ConfigurableApplicationContext application;
    private static Connection rabbitConnection;

    @BeforeAll
    static void startAnalyticsConsumer() throws Exception {
        Task63TestSupport.assertRequiredMigrationTablesExist();
        Task63TestSupport.assertRabbitListenerExistsInMessagingInfrastructure();
        application = Task63TestSupport.startAnalyticsApplication(
                "server.port=0",
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.rabbitmq.host=" + RABBIT.getHost(),
                "spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                "spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                "spring.rabbitmq.password=" + RABBIT.getAdminPassword(),
                "spring.rabbit.listener.simple.concurrency=2",
                "spring.rabbit.listener.simple.max-concurrency=2",
                "spring.rabbit.listener.simple.prefetch=1",
                "spring.rabbit.listener.simple.default-requeue-rejected=false");

        var factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getAmqpPort());
        factory.setUsername(RABBIT.getAdminUsername());
        factory.setPassword(RABBIT.getAdminPassword());
        rabbitConnection = factory.newConnection("task-6.3-consumer-test");
    }

    @AfterAll
    static void stopResources() throws Exception {
        if (rabbitConnection != null) {
            rabbitConnection.close();
        }
        if (application != null) {
            application.close();
        }
    }

    @BeforeEach
    void cleanAnalyticsState() throws Exception {
        dropFailureTriggers();
        try (var connection = analyticsConnection()) {
            connection.createStatement().executeUpdate("delete from processed_events");
            connection.createStatement().executeUpdate("delete from analytics_bets");
        }
        try (var channel = rabbitConnection.createChannel()) {
            channel.queuePurge(MessagingConstants.ANALYTICS_BETTING_EVENTS_QUEUE);
        }
    }

    @Test
    void should_consume_created_updated_and_settled_events_through_rabbitmq_into_one_projection() throws Exception {
        publish(createdEvent(UUID.fromString("66666666-6666-6666-6666-666666666666")),
                MessagingConstants.BET_CREATED_ROUTING_KEY);
        awaitProjection();
        assertThat(readProjection()).satisfies(row -> {
            assertThat(row.status()).isEqualTo("PENDING");
            assertThat(row.createdAt()).isEqualTo(CREATED_OCCURRED_AT);
            assertThat(row.updatedAt()).isEqualTo(CREATED_OCCURRED_AT);
            assertThat(row.profit()).isNull();
            assertThat(row.returnAmount()).isNull();
            assertThat(row.settledAt()).isNull();
        });

        publish(updatedEvent(UUID.fromString("77777777-7777-7777-7777-777777777777")),
                MessagingConstants.BET_UPDATED_ROUTING_KEY);
        awaitUpdatedTimestamp();
        assertThat(readProjection()).satisfies(row -> {
            assertThat(row.sport()).isEqualTo("TENNIS");
            assertThat(row.odds()).isEqualByComparingTo("2.1256");
            assertThat(row.stake()).isEqualByComparingTo("120.13");
            assertThat(row.createdAt()).isEqualTo(CREATED_OCCURRED_AT);
            assertThat(row.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(row.status()).isEqualTo("PENDING");
        });

        publish(settledEvent(UUID.fromString("88888888-8888-8888-8888-888888888888")),
                MessagingConstants.BET_SETTLED_ROUTING_KEY);
        awaitSettled();
        assertThat(readProjection()).satisfies(row -> {
            assertThat(row.status()).isEqualTo("CASHOUT");
            assertThat(row.odds()).isEqualByComparingTo("2.1256");
            assertThat(row.stake()).isEqualByComparingTo("120.13");
            assertThat(row.profit()).isEqualByComparingTo("11.11");
            assertThat(row.returnAmount()).isEqualByComparingTo("88.88");
            assertThat(row.settledAt()).isEqualTo(SETTLED_AT);
            assertThat(row.updatedAt()).isEqualTo(SETTLED_OCCURRED_AT);
        });
        assertThat(processedEventCount()).isEqualTo(3);
    }

    @Test
    void should_ignore_duplicate_event_id_but_process_distinct_event_ids_for_the_same_bet() throws Exception {
        var create = createdEvent(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        publish(create, MessagingConstants.BET_CREATED_ROUTING_KEY);
        publish(create, MessagingConstants.BET_CREATED_ROUTING_KEY);
        awaitProjection();
        awaitProcessedCount(1);

        publish(updatedEvent(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")),
                MessagingConstants.BET_UPDATED_ROUTING_KEY);
        awaitUpdatedTimestamp();
        assertThat(processedEventCount()).isEqualTo(2);
        assertThat(projectionCount()).isEqualTo(1);
    }

    @Test
    void should_handle_concurrent_duplicate_deliveries_with_one_projection_and_one_processed_event() throws Exception {
        var envelope = createdEvent(UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"));
        var start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    start.await();
                    publish(envelope, MessagingConstants.BET_CREATED_ROUTING_KEY);
                    return null;
                });
            }
            start.countDown();
        }
        awaitProjection();
        awaitProcessedCount(1);
        assertThat(projectionCount()).isEqualTo(1);
        assertThat(processedEventCount()).isEqualTo(1);
    }

    @Test
    void should_complete_concurrent_duplicate_application_invocations_successfully() throws Exception {
        var processor = Task63TestSupport.requireApplicationProcessor();
        var target = application.getBean(processor.method().getDeclaringClass());
        var envelope = createdEvent(UUID.fromString("18181818-1818-1818-1818-181818181818"));
        var start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                processor.invoke(target, envelope);
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                processor.invoke(target, envelope);
                return null;
            });

            start.countDown();
            first.get();
            second.get();
        }

        awaitProjection();
        awaitProcessedCount(1);
        assertThat(projectionCount()).isEqualTo(1);
        assertThat(processedEventCount()).isEqualTo(1);
    }

    @Test
    void should_not_mutate_or_register_malformed_invalid_or_out_of_order_events() throws Exception {
        var unknownType = "{\"eventId\":\"cccccccc-cccc-cccc-cccc-cccccccccccc\","
                + "\"eventType\":\"BET_UNKNOWN\",\"occurredAt\":\"2026-08-18T10:00:00Z\","
                + "\"version\":1,\"producer\":\"betting-service\",\"payload\":{}}";
        var invalidVersion = JSON.writeValueAsString(createdEvent(UUID.fromString(
                "dddddddd-dddd-dddd-dddd-dddddddddddd"))).replace("\"version\":1", "\"version\":2");
        var invalidProducer = JSON.writeValueAsString(createdEvent(UUID.fromString(
                "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"))).replace("betting-service", "other-service");
        var malformedPayload = "{\"eventId\":\"ffffffff-ffff-ffff-ffff-ffffffffffff\","
                + "\"eventType\":\"BET_CREATED\",\"occurredAt\":\"2026-08-18T10:00:00Z\","
                + "\"version\":1,\"producer\":\"betting-service\",\"payload\":{\"betId\":null}}";

        publishRaw(unknownType, MessagingConstants.BET_CREATED_ROUTING_KEY);
        publishRaw(invalidVersion, MessagingConstants.BET_CREATED_ROUTING_KEY);
        publishRaw(invalidProducer, MessagingConstants.BET_CREATED_ROUTING_KEY);
        publishRaw("not-json", MessagingConstants.BET_CREATED_ROUTING_KEY);
        publishRaw(malformedPayload, MessagingConstants.BET_CREATED_ROUTING_KEY);
        publish(updatedEvent(UUID.fromString("12121212-1212-1212-1212-121212121212")),
                MessagingConstants.BET_UPDATED_ROUTING_KEY);
        publish(settledEvent(UUID.fromString("13131313-1313-1313-1313-131313131313")),
                MessagingConstants.BET_SETTLED_ROUTING_KEY);

        Task63TestSupport.awaitQueueSettled(RABBIT);
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(projectionCount()).isZero();
            assertThat(processedEventCount()).isZero();
        });
    }

    @Test
    void should_reject_a_second_created_event_without_overwriting_the_existing_projection() throws Exception {
        publish(createdEvent(UUID.fromString("14141414-1414-1414-1414-141414141414")),
                MessagingConstants.BET_CREATED_ROUTING_KEY);
        awaitProjection();
        var before = readProjection();
        var second = new EventEnvelope(
                UUID.fromString("15151515-1515-1515-1515-151515151515"), EventType.BET_CREATED,
                CREATED_OCCURRED_AT.plusSeconds(1), MessagingConstants.VERSION_ONE,
                MessagingConstants.BETTING_SERVICE_PRODUCER,
                new BetCreatedPayload(BET_ID, USER_ID, "CHANGED", "League", "Home", "Away", "MARKET", "Selection",
                        new BigDecimal("9.9999"), new BigDecimal("1.00"), BetStatus.PENDING, PLACED_AT));
        publish(second, MessagingConstants.BET_CREATED_ROUTING_KEY);
        Task63TestSupport.awaitQueueSettled(RABBIT);
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(projectionCount()).isEqualTo(1);
            assertThat(processedEventCount()).isEqualTo(1);
        });
        assertThat(readProjection()).isEqualTo(before);
    }

    @Test
    void should_rollback_processed_event_registration_when_projection_persistence_fails() throws Exception {
        installProjectionFailureTrigger();
        try {
            assertThatThrownBy(() -> invokeApplicationProcessor(
                    createdEventWithIds(PROCESSED_EVENT_FAILURE_ID, PROJECTION_FAILURE_BET_ID)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(projectionCount()).isZero();
            assertThat(processedEventCount()).isZero();
        } finally {
            dropFailureTriggers();
        }
    }

    @Test
    void should_rollback_projection_mutation_when_processed_event_registration_fails() throws Exception {
        installProcessedEventFailureTrigger();
        try {
            assertThatThrownBy(() -> invokeApplicationProcessor(
                    createdEventWithIds(PROCESSED_EVENT_FAILURE_ID, BET_ID)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(projectionCount()).isZero();
            assertThat(processedEventCount()).isZero();
        } finally {
            dropFailureTriggers();
        }
    }

    private static EventEnvelope createdEvent(UUID eventId) {
        return createdEventWithIds(eventId, BET_ID);
    }

    private static EventEnvelope createdEventWithIds(UUID eventId, UUID betId) {
        return new EventEnvelope(eventId, EventType.BET_CREATED, CREATED_OCCURRED_AT,
                MessagingConstants.VERSION_ONE, MessagingConstants.BETTING_SERVICE_PRODUCER,
                new BetCreatedPayload(betId, USER_ID, "FOOTBALL", "League", "Home", "Away", "MATCH_RESULT", "Home",
                        new BigDecimal("2.1256"), new BigDecimal("120.13"), BetStatus.PENDING, PLACED_AT));
    }

    private static EventEnvelope updatedEvent(UUID eventId) {
        return new EventEnvelope(eventId, EventType.BET_UPDATED, UPDATED_OCCURRED_AT,
                MessagingConstants.VERSION_ONE, MessagingConstants.BETTING_SERVICE_PRODUCER,
                new BetUpdatedPayload(BET_ID, USER_ID, "TENNIS", "Updated League", "Player A", "Player B",
                        "MATCH_WINNER", "Player A", new BigDecimal("2.1256"), new BigDecimal("120.13"),
                        BetStatus.PENDING, PLACED_AT, UPDATED_AT));
    }

    private static EventEnvelope settledEvent(UUID eventId) {
        return new EventEnvelope(eventId, EventType.BET_SETTLED, SETTLED_OCCURRED_AT,
                MessagingConstants.VERSION_ONE, MessagingConstants.BETTING_SERVICE_PRODUCER,
                new BetSettledPayload(BET_ID, USER_ID, BetStatus.CASHOUT, new BigDecimal("2.1256"),
                        new BigDecimal("120.13"), new BigDecimal("11.11"), new BigDecimal("88.88"), SETTLED_AT));
    }

    private static void publish(EventEnvelope envelope, String routingKey) {
        try {
            publishRaw(JSON.writeValueAsString(envelope), routingKey);
        } catch (Exception exception) {
            throw new AssertionError("could not serialize test event", exception);
        }
    }

    private static void publishRaw(String body, String routingKey) {
        try (var channel = rabbitConnection.createChannel()) {
            channel.confirmSelect();
            channel.basicPublish(MessagingConstants.BETTING_EVENTS_EXCHANGE, routingKey, null,
                    body.getBytes(StandardCharsets.UTF_8));
            channel.waitForConfirmsOrDie(1_000);
        } catch (Exception exception) {
            throw new AssertionError("could not publish test event", exception);
        }
    }

    private static void awaitProjection() {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(projectionCount()).isEqualTo(1));
    }

    private static void awaitUpdatedTimestamp() {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(readProjection().updatedAt()).isEqualTo(UPDATED_AT));
    }

    private static void awaitSettled() {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(readProjection().status()).isEqualTo("CASHOUT"));
    }

    private static void awaitProcessedCount(int expected) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(processedEventCount()).isEqualTo(expected));
    }

    private static void invokeApplicationProcessor(EventEnvelope envelope) {
        var processor = Task63TestSupport.requireApplicationProcessor();
        var target = application.getBean(processor.method().getDeclaringClass());
        processor.invoke(target, envelope);
    }

    private static int projectionCount() {
        return scalarInt("select count(*) from analytics_bets");
    }

    private static int processedEventCount() {
        return scalarInt("select count(*) from processed_events");
    }

    private static int scalarInt(String sql) {
        try (var connection = analyticsConnection(); var result = connection.createStatement().executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        } catch (SQLException exception) {
            throw new AssertionError("could not query Analytics database", exception);
        }
    }

    private static ProjectionRow readProjection() {
        try (var connection = analyticsConnection();
                var result = connection.createStatement().executeQuery(
                        "select sport, odds, stake, status, profit, return_amount, placed_at, settled_at, "
                                + "created_at, updated_at from analytics_bets where bet_id = '44444444-4444-4444-4444-444444444444'")) {
            assertThat(result.next()).isTrue();
            return new ProjectionRow(
                    result.getString("sport"), result.getBigDecimal("odds"), result.getBigDecimal("stake"),
                    result.getString("status"), result.getBigDecimal("profit"), result.getBigDecimal("return_amount"),
                    readInstant(result, "placed_at"), readInstant(result, "settled_at"),
                    readInstant(result, "created_at"), readInstant(result, "updated_at"));
        } catch (SQLException exception) {
            throw new AssertionError("could not query Analytics projection", exception);
        }
    }

    private static Instant readInstant(java.sql.ResultSet result, String column) throws SQLException {
        var value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static java.sql.Connection analyticsConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void installProjectionFailureTrigger() throws SQLException {
        try (var connection = analyticsConnection(); var statement = connection.createStatement()) {
            statement.execute("create or replace function task63_fail_projection() returns trigger language plpgsql "
                    + "as $$ begin raise exception 'task 6.3 projection fault injection'; end; $$");
            statement.execute("create trigger task63_projection_failure before insert on analytics_bets "
                    + "for each row when (NEW.bet_id = '16161616-1616-1616-1616-161616161616'::uuid) "
                    + "execute function task63_fail_projection()");
        }
    }

    private static void installProcessedEventFailureTrigger() throws SQLException {
        try (var connection = analyticsConnection(); var statement = connection.createStatement()) {
            statement.execute("create or replace function task63_fail_processed_event() returns trigger language plpgsql "
                    + "as $$ begin raise exception 'task 6.3 processed-event fault injection'; end; $$");
            statement.execute("create trigger task63_processed_event_failure before insert on processed_events "
                    + "for each row when (NEW.event_id = '17171717-1717-1717-1717-171717171717'::uuid) "
                    + "execute function task63_fail_processed_event()");
        }
    }

    private static void dropFailureTriggers() throws SQLException {
        try (var connection = analyticsConnection(); var statement = connection.createStatement()) {
            statement.execute("drop trigger if exists task63_projection_failure on analytics_bets");
            statement.execute("drop trigger if exists task63_processed_event_failure on processed_events");
            statement.execute("drop function if exists task63_fail_projection()");
            statement.execute("drop function if exists task63_fail_processed_event()");
        }
    }

    private record ProjectionRow(
            String sport,
            BigDecimal odds,
            BigDecimal stake,
            String status,
            BigDecimal profit,
            BigDecimal returnAmount,
            Instant placedAt,
            Instant settledAt,
            Instant createdAt,
            Instant updatedAt) {
    }
}
