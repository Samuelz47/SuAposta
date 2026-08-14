package com.suaposta.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

final class ApplicationTestSupport {

    private ApplicationTestSupport() {
    }

    static ConfigurableApplicationContext startApplication() {
        return startApplication(Map.of());
    }

    static ConfigurableApplicationContext startApplication(Map<String, String> propertyOverrides) {
        var previousSecret = System.getProperty("JWT_SECRET");
        var previousProperties = new HashMap<String, String>();
        System.setProperty("JWT_SECRET", "task-3-3-only-signing-secret-32-bytes");
        propertyOverrides.forEach((name, value) -> {
            previousProperties.put(name, System.getProperty(name));
            System.setProperty(name, value);
        });
        try {
            return new SpringApplicationBuilder(findApplicationClass()).run();
        } finally {
            if (previousSecret == null) {
                System.clearProperty("JWT_SECRET");
            } else {
                System.setProperty("JWT_SECRET", previousSecret);
            }
            propertyOverrides.keySet().forEach(name -> {
                var previousValue = previousProperties.get(name);
                if (previousValue == null) {
                    System.clearProperty(name);
                } else {
                    System.setProperty(name, previousValue);
                }
            });
        }
    }

    private static Class<?> findApplicationClass() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(SpringBootConfiguration.class));

        var candidates = scanner.findCandidateComponents("com.suaposta");
        assertThat(candidates)
                .as("API Gateway must provide exactly one Spring Boot application class")
                .hasSize(1);

        var className = candidates.iterator().next().getBeanClassName();
        try {
            return ClassUtils.forName(className, ApplicationTestSupport.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new AssertionError("Spring Boot application class cannot be loaded: " + className, exception);
        }
    }
}
