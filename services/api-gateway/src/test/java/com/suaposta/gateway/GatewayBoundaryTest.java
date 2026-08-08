package com.suaposta.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class GatewayBoundaryTest {

    @Test
    void should_not_contain_domain_persistence_or_business_packages() {
        assertThat(productionFiles())
                .noneMatch(path -> {
                    var normalized = path.toString().replace('\\', '/')
                            .toLowerCase(Locale.ROOT);
                    return normalized.matches(".*(?:/domain/|/persistence/|/repository/|/entity/|/bet/|/analytics/|/user/|/auth/).*");
                });
    }

    @Test
    void should_not_configure_databases_or_messaging_in_gateway() {
        var contents = productionFiles().stream()
                .map(GatewayBoundaryTest::read)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();

        assertThat(contents)
                .allMatch(value -> !value.contains("datasource")
                        && !value.contains("jdbc")
                        && !value.contains("postgres")
                        && !value.contains("flyway")
                        && !value.contains("rabbit")
                        && !value.contains("amqp"));
    }

    private static java.util.List<Path> productionFiles() {
        var sourceRoot = Path.of("src/main");
        assertThat(sourceRoot).as("API Gateway production source root must exist").isDirectory();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths.filter(Files::isRegularFile).toList();
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
