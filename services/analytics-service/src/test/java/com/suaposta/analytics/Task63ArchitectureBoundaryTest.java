package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Task63ArchitectureBoundaryTest {

    private static final List<String> RABBITMQ_IMPLEMENTATION_TYPES = List.of(
            "org.springframework.amqp", "com.rabbitmq", "RabbitTemplate", "RabbitAdmin", "RabbitListener");
    private static final List<String> BETTING_PERSISTENCE_TYPES = List.of(
            "com.suaposta.betting", "BetEntity", "SpringDataBetRepository", "JpaBetRepositoryAdapter", "betting_db");
    private static final List<String> ADVANCED_MESSAGING_TYPES = List.of(
            "DeadLetter", "dead-letter", "DLQ", "DLX", "@Retryable", "RetryInterceptor", "outbox");

    @Test
    void should_keep_rabbit_listener_code_inside_analytics_infrastructure_messaging() {
        var sources = Task63TestSupport.javaSources(Task63TestSupport.MAIN_JAVA);
        var listenerSources = sources.stream()
                .filter(path -> containsAny(path, "@RabbitListener", "RabbitListener"))
                .toList();

        assertThat(listenerSources)
                .as("Task 6.3 must provide a Rabbit listener")
                .isNotEmpty();
        assertThat(listenerSources)
                .as("Rabbit listener code must be under infrastructure/messaging")
                .allMatch(path -> path.toString().contains("infrastructure/messaging"));
    }

    @Test
    void should_keep_application_and_domain_free_of_rabbitmq_implementation_types() {
        var application = Task63TestSupport.javaSources(
                Task63TestSupport.MAIN_JAVA.resolve("com/suaposta/analytics/application"));
        var domain = Task63TestSupport.javaSources(
                Task63TestSupport.MAIN_JAVA.resolve("com/suaposta/analytics/domain"));

        assertThat(Stream.concat(application.stream(), domain.stream()).toList())
                .as("Analytics application/domain must depend on neutral ports, not RabbitMQ")
                .allMatch(path -> containsNone(path, RABBITMQ_IMPLEMENTATION_TYPES));
    }

    @Test
    void should_keep_analytics_production_code_independent_of_betting_persistence() {
        assertThat(Task63TestSupport.javaSources(Task63TestSupport.MAIN_JAVA))
                .as("Analytics must consume event data and never import Betting persistence")
                .allMatch(path -> containsNone(path, BETTING_PERSISTENCE_TYPES));
    }

    @Test
    void should_not_introduce_advanced_retry_dead_letter_or_outbox_scope() {
        assertThat(Task63TestSupport.javaSources(Task63TestSupport.MAIN_JAVA))
                .as("Task 6.3 does not include advanced messaging recovery infrastructure")
                .allMatch(path -> containsNone(path, ADVANCED_MESSAGING_TYPES));
    }

    private static boolean containsAny(Path path, String... fragments) {
        var source = Task63TestSupport.read(path);
        return Stream.of(fragments).anyMatch(source::contains);
    }

    private static boolean containsNone(Path path, List<String> fragments) {
        var source = Task63TestSupport.read(path);
        return fragments.stream().noneMatch(source::contains);
    }
}
