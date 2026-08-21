package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Task71DashboardArchitectureBoundaryTest {

    private static final Path MAIN = Path.of("src/main/java/com/suaposta/analytics");

    @Test
    void should_keep_all_analytics_production_code_independent_of_betting_implementation() {
        assertSourcesDoNotContain(MAIN, "com.suaposta.betting");
    }

    @Test
    void should_keep_presentation_free_of_sql_and_datasource_implementation_details() {
        assertSourcesDoNotContain(
                MAIN.resolve("presentation"),
                "JdbcTemplate", "DataSource", "java.sql", "select ", "from analytics_bets", "processed_events");
    }

    @Test
    void should_keep_application_and_domain_independent_of_http_and_rabbitmq_implementation() {
        for (var layer : List.of("application", "domain")) {
            assertSourcesDoNotContain(
                    MAIN.resolve(layer),
                    "jakarta.servlet", "HttpServletRequest", "HttpServletResponse",
                    "org.springframework.web", "RabbitTemplate", "@RabbitListener", "com.rabbitmq");
        }
    }

    @Test
    void should_keep_dashboard_read_code_independent_of_rabbitmq_implementation_types() {
        var dashboardSources = javaSources(MAIN).stream()
                .filter(path -> {
                    var text = read(path).toLowerCase(java.util.Locale.ROOT);
                    return text.contains("dashboard") || text.contains("summary");
                })
                .toList();
        for (var source : dashboardSources) {
            assertThat(read(source))
                    .as("Dashboard read source %s must not depend on RabbitMQ", source)
                    .doesNotContain(
                            "RabbitTemplate", "@RabbitListener", "com.rabbitmq", "org.springframework.amqp");
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
