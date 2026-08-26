package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Task72BankrollEvolutionArchitectureBoundaryTest {

    private static final Path MAIN = Path.of("src/main/java/com/suaposta/analytics");

    @Test
    void should_keep_analytics_production_code_independent_of_betting_implementation() {
        assertSourcesDoNotContain(MAIN, "com.suaposta.betting");
    }

    @Test
    void should_keep_bankroll_application_and_domain_code_independent_of_http_and_messaging() {
        for (var layer : List.of("application", "domain")) {
            assertSourcesDoNotContain(
                    MAIN.resolve(layer),
                    "jakarta.servlet", "HttpServletRequest", "HttpServletResponse",
                    "org.springframework.web", "RabbitTemplate", "@RabbitListener", "com.rabbitmq");
        }
    }

    @Test
    void should_keep_analytics_presentation_free_of_sql_and_datasource_details() {
        assertSourcesDoNotContain(
                MAIN.resolve("presentation"),
                "JdbcTemplate", "DataSource", "java.sql", "select ", "from analytics_bets", "processed_events");
    }

    @Test
    void should_keep_identifiable_bankroll_read_path_independent_of_rabbitmq() {
        var readPathSources = javaSources(MAIN).stream()
                .filter(path -> {
                    var source = read(path).toLowerCase(java.util.Locale.ROOT);
                    return source.contains("bankroll") || source.contains("evolution");
                })
                .toList();
        for (var source : readPathSources) {
            assertThat(read(source)).as("Bankroll/evolution read path must not depend on RabbitMQ", source)
                    .doesNotContain(
                            "RabbitTemplate", "@RabbitListener", "org.springframework.amqp", "com.rabbitmq");
        }
    }

    @Test
    void should_keep_bankroll_read_code_free_of_floating_point_financial_arithmetic() {
        var bankrollSources = javaSources(MAIN).stream()
                .filter(path -> read(path).toLowerCase(java.util.Locale.ROOT).contains("bankroll"))
                .toList();
        for (var source : bankrollSources) {
            assertThat(read(source)).as("Bankroll source %s must not use floating point", source)
                    .doesNotContain("double", "float", "Double", "Float");
        }
    }

    private static void assertSourcesDoNotContain(Path root, String... forbidden) {
        for (var source : javaSources(root)) {
            assertThat(read(source)).as("architecture boundary for %s", source).doesNotContain(forbidden);
        }
    }

    private static List<Path> javaSources(Path root) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
