package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class BettingLayerStructureTest {

    private static final Set<String> REQUIRED_LAYERS = Set.of(
            "domain", "application", "infrastructure", "presentation");

    @Test
    void should_define_all_documented_architectural_packages() {
        var sourceRoot = Path.of("src/main/java");
        assertThat(sourceRoot).as("Betting Service Java source root must exist").isDirectory();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            var packageNames = paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(packageNames).containsAll(REQUIRED_LAYERS);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
