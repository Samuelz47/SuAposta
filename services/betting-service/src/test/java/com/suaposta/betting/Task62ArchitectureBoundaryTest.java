package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Task62ArchitectureBoundaryTest {

    private static final Path REPOSITORY_ROOT = Path.of("../..").toAbsolutePath().normalize();
    private static final String[] RABBITMQ_FRAGMENTS = {
        "RabbitTemplate", "RabbitAdmin", "com.rabbitmq", "org.springframework.amqp"
    };

    @Test
    void should_keep_betting_domain_and_application_free_of_rabbitmq_implementation_types() {
        assertJavaSourcesDoNotContain(
                REPOSITORY_ROOT.resolve("services/betting-service/src/main/java/com/suaposta/betting/domain"),
                RABBITMQ_FRAGMENTS);
        assertJavaSourcesDoNotContain(
                REPOSITORY_ROOT.resolve(
                        "services/betting-service/src/main/java/com/suaposta/betting/application"),
                RABBITMQ_FRAGMENTS);
    }

    @Test
    void should_keep_the_publisher_adapter_inside_betting_infrastructure() {
        var infrastructure = REPOSITORY_ROOT.resolve(
                "services/betting-service/src/main/java/com/suaposta/betting/infrastructure");
        var nonInfrastructure = REPOSITORY_ROOT.resolve(
                "services/betting-service/src/main/java/com/suaposta/betting")
                .resolve(".");

        var rabbitmqSources = javaSourcesContaining(nonInfrastructure, RABBITMQ_FRAGMENTS);
        var infrastructureSources = javaSourcesContaining(infrastructure, RABBITMQ_FRAGMENTS);

        assertThat(rabbitmqSources)
                .as("RabbitMQ-specific references must remain inside infrastructure")
                .containsExactlyElementsOf(infrastructureSources);
    }

    @Test
    void should_define_a_neutral_application_publisher_port_without_rabbitmq_types() throws IOException {
        var port = REPOSITORY_ROOT.resolve(
                "services/betting-service/src/main/java/com/suaposta/betting/application/port/out/BetEventPublisher.java");
        assertThat(port).as("Task 6.2 application publisher port must exist").isRegularFile();
        var source = Files.readString(port);
        assertThat(source).doesNotContain(RABBITMQ_FRAGMENTS);
    }

    private static void assertJavaSourcesDoNotContain(Path root, String... forbiddenFragments) {
        for (var source : javaSources(root).toList()) {
            try {
                assertThat(Files.readString(source))
                        .as("%s must preserve the Task 6.2 architecture boundary", source)
                        .doesNotContain(forbiddenFragments);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private static Stream<Path> javaSources(Path root) {
        var normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRoot)) {
            return Stream.empty();
        }
        try {
            return Files.walk(normalizedRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList()
                    .stream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static java.util.List<Path> javaSourcesContaining(Path root, String... fragments) {
        return javaSources(root)
                .filter(path -> {
                    try {
                        var source = Files.readString(path);
                        return Stream.of(fragments).anyMatch(source::contains);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                })
                .toList();
    }
}
