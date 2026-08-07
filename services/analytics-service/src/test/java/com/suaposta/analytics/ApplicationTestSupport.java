package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

final class ApplicationTestSupport {

    private ApplicationTestSupport() {
    }

    static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(findApplicationClass()).run();
    }

    private static Class<?> findApplicationClass() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(SpringBootConfiguration.class));

        var candidates = scanner.findCandidateComponents("com.suaposta");
        assertThat(candidates)
                .as("Analytics Service must provide exactly one Spring Boot application class")
                .hasSize(1);

        var className = candidates.iterator().next().getBeanClassName();
        try {
            return ClassUtils.forName(className, ApplicationTestSupport.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new AssertionError("Spring Boot application class cannot be loaded: " + className, exception);
        }
    }
}
