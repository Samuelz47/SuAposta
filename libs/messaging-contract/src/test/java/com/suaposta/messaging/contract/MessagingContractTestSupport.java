package com.suaposta.messaging.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

final class MessagingContractTestSupport {

    static final UUID EVENT_ID = UUID.fromString("3df04e41-6a77-4c8e-9c6f-b663d68c1c92");
    static final UUID BET_ID = UUID.fromString("f8c6eb32-54d4-4024-9581-7a0a8d6f4f19");
    static final UUID USER_ID = UUID.fromString("b40da580-a017-4a11-bd42-c67aa6409166");
    static final Instant OCCURRED_AT = Instant.parse("2026-07-21T21:00:00Z");
    static final Instant PLACED_AT = Instant.parse("2026-07-21T20:30:00Z");
    static final Instant UPDATED_AT = Instant.parse("2026-07-21T21:20:00Z");
    static final Instant SETTLED_AT = Instant.parse("2026-07-21T22:00:00Z");

    static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .nodeFactory(new JsonNodeFactory(true))
            .build();

    private MessagingContractTestSupport() {
    }

    static Class<?> productionTypeWithExactProperties(String description, String... properties) {
        var expected = Set.of(properties);
        var matches = productionClasses().stream()
                .filter(type -> propertyNames(type).equals(expected))
                .toList();

        assertThat(matches)
                .as("Task 6.1 must provide one neutral production type for %s with properties %s",
                        description, expected)
                .hasSize(1);
        return matches.getFirst();
    }

    static Class<?> productionTypeContainingConstants(Set<Object> expectedConstants) {
        var matches = productionClasses().stream()
                .filter(type -> publicConstants(type).containsAll(expectedConstants))
                .toList();

        assertThat(matches)
                .as("Task 6.1 must provide one neutral public constants type containing %s", expectedConstants)
                .hasSize(1);
        return matches.getFirst();
    }

    static List<Class<?>> productionClasses() {
        var matches = new ArrayList<Class<?>>();
        for (var entry : System.getProperty("java.class.path").split(System.getProperty("path.separator"))) {
            var root = Path.of(entry);
            if (!Files.isDirectory(root) || !normalized(root).contains("/classes/java/main")) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> !path.toString().contains("$"))
                        .map(path -> className(root, path))
                        .map(MessagingContractTestSupport::loadClass)
                        .forEach(matches::add);
            } catch (IOException exception) {
                throw new AssertionError("Unable to inspect compiled messaging contract classes", exception);
            }
        }

        return matches;
    }

    static JsonNode roundTrip(String source, Class<?> targetType) throws Exception {
        var value = JSON.readValue(source, targetType);
        return JSON.readTree(JSON.writeValueAsBytes(value));
    }

    static void assertExactFields(JsonNode node, String... expectedFields) {
        assertThat(node.isObject()).isTrue();
        var actual = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        assertThat(actual).containsExactlyInAnyOrder(expectedFields);
    }

    static void assertDecimal(JsonNode node, String field, String expected) {
        assertThat(node.path(field).isNumber()).as("%s must be a JSON number", field).isTrue();
        assertThat(node.path(field).decimalValue()).isEqualByComparingTo(new BigDecimal(expected));
    }

    static void assertPropertyType(Class<?> type, String property, Class<?> expectedType) {
        assertThat(propertyType(type, property))
                .as("%s.%s must preserve the documented Java type", type.getSimpleName(), property)
                .isEqualTo(expectedType);
    }

    static Class<?> propertyType(Class<?> type, String property) {
        if (type.isRecord()) {
            return Arrays.stream(type.getRecordComponents())
                    .filter(component -> component.getName().equals(property))
                    .map(RecordComponent::getType)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing property " + property + " on " + type));
        }

        for (var current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(property).getType();
            } catch (NoSuchFieldException ignored) {
                // A bean-style accessor is also an acceptable public contract.
            }
        }

        var capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        return Arrays.stream(type.getMethods())
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> method.getName().equals(property)
                        || method.getName().equals("get" + capitalized)
                        || method.getName().equals("is" + capitalized))
                .map(java.lang.reflect.Method::getReturnType)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing property " + property + " on " + type));
    }

    static Set<Object> publicConstants(Class<?> type) {
        var values = new LinkedHashSet<>();
        Arrays.stream(type.getFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isFinal(field.getModifiers()))
                .forEach(field -> {
                    try {
                        values.add(field.get(null));
                    } catch (IllegalAccessException exception) {
                        throw new AssertionError("Unable to read public messaging constant " + field, exception);
                    }
                });
        return values;
    }

    static Set<String> propertyNames(Class<?> type) {
        var names = new LinkedHashSet<String>();
        if (type.isRecord()) {
            Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).forEach(names::add);
            return names;
        }
        for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            Arrays.stream(current.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .map(java.lang.reflect.Field::getName)
                    .forEach(names::add);
        }
        if (!names.isEmpty()) {
            return names;
        }
        Arrays.stream(type.getMethods())
                .filter(method -> method.getParameterCount() == 0)
                .map(java.lang.reflect.Method::getName)
                .filter(name -> !name.equals("getClass"))
                .filter(name -> name.startsWith("get") || name.startsWith("is"))
                .map(name -> name.startsWith("get") ? name.substring(3) : name.substring(2))
                .filter(name -> !name.isEmpty())
                .map(name -> Character.toLowerCase(name.charAt(0)) + name.substring(1))
                .forEach(names::add);
        return names;
    }

    static String envelopeJson(String eventType, int version, String producer, String eventId,
            String occurredAt, String payload) {
        return """
                {
                  "eventId": %s,
                  "eventType": %s,
                  "occurredAt": %s,
                  "version": %d,
                  "producer": %s,
                  "payload": %s
                }
                """.formatted(jsonString(eventId), jsonString(eventType), jsonString(occurredAt), version,
                        jsonString(producer), payload);
    }

    static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String normalized(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static String className(Path root, Path classFile) {
        var relative = root.relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace('/', '.')
                .replace('\\', '.');
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, MessagingContractTestSupport.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new AssertionError("Unable to load messaging contract class " + name, exception);
        }
    }
}
