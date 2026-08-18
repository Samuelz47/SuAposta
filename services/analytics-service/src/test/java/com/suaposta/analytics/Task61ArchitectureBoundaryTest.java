package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Task61ArchitectureBoundaryTest {

    private static final Path REPOSITORY_ROOT = Path.of("../..").toAbsolutePath().normalize();

    @Test
    void should_keep_betting_domain_independent_of_rabbitmq_and_spring_amqp() {
        assertJavaSourcesDoNotContain(
                REPOSITORY_ROOT.resolve("services/betting-service/src/main/java/com/suaposta/betting/domain"),
                "org.springframework.amqp", "com.rabbitmq", "RabbitTemplate", "RabbitAdmin");
    }

    @Test
    void should_keep_the_neutral_contract_independent_of_services_persistence_and_messaging_clients() {
        assertJavaSourcesDoNotContain(
                REPOSITORY_ROOT.resolve("libs/messaging-contract/src/main/java"),
                "org.springframework", "org.springframework.amqp", "com.rabbitmq", "jakarta.persistence",
                "org.hibernate", "com.suaposta.betting", "com.suaposta.analytics", "Entity", "Repository");
    }

    @Test
    void should_not_couple_analytics_application_or_domain_to_betting_implementation() {
        assertJavaSourcesDoNotContain(
                REPOSITORY_ROOT.resolve("services/analytics-service/src/main/java/com/suaposta/analytics/application"),
                "com.suaposta.betting");
        assertJavaSourcesDoNotContain(
                REPOSITORY_ROOT.resolve("services/analytics-service/src/main/java/com/suaposta/analytics/domain"),
                "com.suaposta.betting");
    }

    @Test
    void should_keep_betting_application_services_independent_of_rabbitmq_implementation() {
        var services = REPOSITORY_ROOT.resolve(
                "services/betting-service/src/main/java/com/suaposta/betting/application/service");
        assertJavaSourcesDoNotContain(services,
                "RabbitTemplate", "com.rabbitmq", "org.springframework.amqp");
    }

    private static void assertJavaSourcesDoNotContain(Path root, String... forbiddenFragments) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            var sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
            for (var source : sources) {
                var text = Files.readString(source);
                assertThat(text)
                        .as("%s must preserve the Task 6.1 architecture boundary", source)
                        .doesNotContain(forbiddenFragments);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
