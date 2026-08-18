package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
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
class Task61RabbitTopologyIntegrationTest {

    private static final String EXCHANGE = "betting.events";
    private static final String QUEUE = "analytics.betting-events.queue";
    private static final Set<String> ROUTING_KEYS = Set.of("bet.created", "bet.updated", "bet.settled");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13.7-management"));

    private static ConfigurableApplicationContext context;
    private static Connection connection;

    @BeforeAll
    static void startAnalyticsAgainstRabbitMq() throws Exception {
        context = new SpringApplicationBuilder(AnalyticsServiceApplication.class)
                .properties(
                        "server.port=0",
                        "spring.rabbitmq.host=" + RABBIT.getHost(),
                        "spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                        "spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                        "spring.rabbitmq.password=" + RABBIT.getAdminPassword())
                .run();

        var factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getAmqpPort());
        factory.setUsername(RABBIT.getAdminUsername());
        factory.setPassword(RABBIT.getAdminPassword());
        connection = factory.newConnection("task-6.1-topology-test");
    }

    @AfterAll
    static void stopResources() throws Exception {
        if (connection != null) {
            connection.close();
        }
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void requireAndPurgeDocumentedQueue() throws Exception {
        assertDocumentedTopology();
        try (var channel = connection.createChannel()) {
            channel.queuePurge(QUEUE);
        }
    }

    @Test
    void should_declare_the_documented_topic_exchange_queue_and_exact_bindings() throws Exception {
        var exchange = managementGet("/api/exchanges/%2F/" + encode(EXCHANGE));
        var queue = managementGet("/api/queues/%2F/" + encode(QUEUE));
        var bindings = managementGet("/api/queues/%2F/" + encode(QUEUE) + "/bindings");

        assertThat(exchange.path("type").asText()).isEqualTo("topic");
        assertThat(queue.path("name").asText()).isEqualTo(QUEUE);
        var routingKeys = Stream.iterate(0, index -> index + 1)
                .limit(bindings.size())
                .map(index -> bindings.get(index))
                .filter(binding -> EXCHANGE.equals(binding.path("source").asText()))
                .map(binding -> binding.path("routing_key").asText())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(routingKeys).containsExactlyInAnyOrderElementsOf(ROUTING_KEYS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentedRoutes")
    void should_route_each_documented_key_to_the_analytics_queue(String routingKey) throws Exception {
        var body = (routingKey + "-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);

        try (var channel = connection.createChannel()) {
            channel.confirmSelect();
            channel.basicPublish(EXCHANGE, routingKey, null, body);
            channel.waitForConfirmsOrDie(1_000);
            var delivery = channel.basicGet(QUEUE, true);
            assertThat(delivery).isNotNull();
            assertThat(delivery.getBody()).isEqualTo(body);
        }
    }

    static Stream<Arguments> documentedRoutes() {
        return ROUTING_KEYS.stream().sorted().map(Arguments::of);
    }

    @Test
    void should_not_route_an_unrelated_key_to_the_analytics_queue() throws Exception {
        try (var channel = connection.createChannel()) {
            channel.confirmSelect();
            channel.basicPublish(EXCHANGE, "bet.unrelated", null, "unrelated".getBytes(StandardCharsets.UTF_8));
            channel.waitForConfirmsOrDie(1_000);
            assertThat(channel.basicGet(QUEUE, true)).isNull();
        }
    }

    private static void assertDocumentedTopology() throws Exception {
        var exchangeResponse = managementResponse("/api/exchanges/%2F/" + encode(EXCHANGE));
        var queueResponse = managementResponse("/api/queues/%2F/" + encode(QUEUE));
        assertThat(exchangeResponse.statusCode())
                .as("Task 6.1 must declare exchange %s", EXCHANGE)
                .isEqualTo(200);
        assertThat(queueResponse.statusCode())
                .as("Task 6.1 must declare queue %s", QUEUE)
                .isEqualTo(200);
    }

    private static JsonNode managementGet(String path) throws Exception {
        var response = managementResponse(path);
        assertThat(response.statusCode()).as("RabbitMQ management GET %s", path).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static HttpResponse<String> managementResponse(String path) throws Exception {
        var credentials = RABBIT.getAdminUsername() + ":" + RABBIT.getAdminPassword();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + RABBIT.getHost() + ":" + RABBIT.getMappedPort(15672) + path))
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
