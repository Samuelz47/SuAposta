package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suaposta.messaging.contract.EventEnvelope;
import com.suaposta.messaging.contract.MessagingConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.rabbitmq.RabbitMQContainer;

final class Task63TestSupport {

    static final Path SERVICE_ROOT = Path.of(".").toAbsolutePath().normalize();
    static final Path MAIN_JAVA = SERVICE_ROOT.resolve("src/main/java");
    static final Path MAIN_RESOURCES = SERVICE_ROOT.resolve("src/main/resources");
    static final Instant FIXED_PROCESSED_AT = Instant.parse("2026-08-18T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private Task63TestSupport() {
    }

    static ConfigurableApplicationContext startAnalyticsApplication(String... properties) {
        return new SpringApplicationBuilder(AnalyticsServiceApplication.class)
                .properties(properties)
                .run();
    }

    static void awaitQueueSettled(RabbitMQContainer rabbit) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> queueHasNoReadyOrUnacknowledgedMessages(rabbit));
    }

    private static boolean queueHasNoReadyOrUnacknowledgedMessages(RabbitMQContainer rabbit) {
        try {
            var credentials = rabbit.getAdminUsername() + ":" + rabbit.getAdminPassword();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + rabbit.getHost() + ":" + rabbit.getMappedPort(15672)
                            + "/api/queues/%2F/" + encode(MessagingConstants.ANALYTICS_BETTING_EVENTS_QUEUE)))
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            JsonNode queue = JSON.readTree(response.body());
            return queue.path("messages_ready").asInt(-1) == 0
                    && queue.path("messages_unacknowledged").asInt(-1) == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static List<Path> javaSources(Path root) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static void assertRequiredMigrationTablesExist() {
        var migrations = MAIN_RESOURCES.resolve("db/migration");
        assertThat(Files.exists(migrations))
                .as("Task 6.3 must provide Analytics Flyway migrations")
                .isTrue();
        var migrationText = javaSourcesOrResources(migrations);
        assertThat(migrationText)
                .as("Task 6.3 migrations must create both contractual tables")
                .contains("analytics_bets", "processed_events");
    }

    static void assertRabbitListenerExistsInMessagingInfrastructure() {
        var messaging = MAIN_JAVA.resolve("com/suaposta/analytics/infrastructure/messaging");
        var listenerSources = javaSources(messaging).stream()
                .map(Task63TestSupport::read)
                .filter(source -> source.contains("@RabbitListener") || source.contains("RabbitListener"))
                .toList();
        assertThat(listenerSources)
                .as("Analytics consumer listener must be implemented in infrastructure/messaging")
                .isNotEmpty();
    }

    static ProcessorMethod requireApplicationProcessor() {
        var candidates = applicationClasses().stream()
                .flatMap(type -> Stream.of(type.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0].equals(EventEnvelope.class))
                .toList();

        var listenerSources = javaSources(MAIN_JAVA.resolve("com/suaposta/analytics/infrastructure/messaging"))
                .stream()
                .map(Task63TestSupport::read)
                .toList();
        var listenerLinkedCandidates = candidates.stream()
                .filter(method -> listenerSources.stream()
                        .anyMatch(source -> source.contains(method.getDeclaringClass().getName())))
                .toList();
        var selectedCandidates = listenerLinkedCandidates.isEmpty() ? candidates : listenerLinkedCandidates;

        assertThat(selectedCandidates)
                .as("Analytics listener must delegate to one clear application EventEnvelope boundary")
                .hasSize(1);
        return new ProcessorMethod(selectedCandidates.get(0));
    }

    static List<Class<?>> applicationClasses() {
        var root = MAIN_JAVA.resolve("com/suaposta/analytics/application");
        return javaSources(root).stream()
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .map(path -> className(path, MAIN_JAVA))
                .map(Task63TestSupport::loadClass)
                .toList();
    }

    static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String javaSourcesOrResources(Path root) {
        if (!Files.exists(root)) {
            return "";
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(Task63TestSupport::read)
                    .reduce("", String::concat);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String className(Path path, Path sourceRoot) {
        var relative = sourceRoot.relativize(path).toString()
                .replace(Path.of("/").toString(), ".")
                .replace('\\', '.')
                .replace(".java", "");
        return relative;
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new AssertionError("Analytics application class cannot be loaded: " + className, exception);
        }
    }

    record ProcessorMethod(Method method) {
        Object invoke(Object target, EventEnvelope envelope) {
            try {
                method.setAccessible(true);
                return method.invoke(target, envelope);
            } catch (IllegalAccessException exception) {
                throw new AssertionError("Analytics application processing seam is not invokable", exception);
            } catch (InvocationTargetException exception) {
                var cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new AssertionError("Analytics application processing failed", cause);
            }
        }
    }

    static FakeApplicationDependencies constructProcessor(ProcessorMethod processor) {
        var constructors = List.of(processor.method().getDeclaringClass().getDeclaredConstructors());
        for (var constructor : constructors) {
            var state = new FakeApplicationDependencies();
            var arguments = new ArrayList<>();
            var supported = true;
            for (var parameterType : constructor.getParameterTypes()) {
                var argument = state.argumentFor(parameterType);
                if (argument == null) {
                    supported = false;
                    break;
                }
                arguments.add(argument);
            }
            if (supported) {
                try {
                    constructor.setAccessible(true);
                    return state.withTarget(constructor.newInstance(arguments.toArray()));
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError("Analytics application seam could not be constructed with test doubles", exception);
                }
            }
        }
        throw new AssertionError(
                "Analytics application seam has no constructor that can be exercised with persistence-port test doubles");
    }

    static final class FakeApplicationDependencies {
        private final Map<UUID, Object> processed = new ConcurrentHashMap<>();
        private final Map<UUID, Object> projections = new ConcurrentHashMap<>();
        private int processedWrites;
        private int projectionWrites;
        private Object target;

        Object argumentFor(Class<?> type) {
            if (type.equals(Clock.class)) {
                return Clock.fixed(FIXED_PROCESSED_AT, ZoneOffset.UTC);
            }
            if (type.isInterface()) {
                return Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> respond(type, method, args));
            }
            return null;
        }

        FakeApplicationDependencies withTarget(Object target) {
            this.target = target;
            return this;
        }

        Object target() {
            return target;
        }

        int processedCount() {
            return processed.size();
        }

        int projectionCount() {
            return projections.size();
        }

        int processedWriteCount() {
            return processedWrites;
        }

        int projectionWriteCount() {
            return projectionWrites;
        }

        private Object respond(Class<?> ignoredPortType, Method method, Object[] args) {
            if (isPersistenceWrite(method, args)) {
                return recordWrite(method, args);
            }

            var key = findUuid(args);
            if (key != null) {
                var value = processed.containsKey(key) ? processed.get(key) : projections.get(key);
                if (method.getReturnType().equals(Optional.class)) {
                    return Optional.ofNullable(value);
                }
                if (method.getReturnType().equals(boolean.class)
                        || method.getReturnType().equals(Boolean.class)) {
                    return value != null;
                }
                return value;
            }
            return defaultValue(method.getReturnType());
        }

        private Object recordWrite(Method method, Object[] args) {
            var value = args == null || args.length == 0 ? null : args[0];
            var valueKey = findUuid(value);
            var processedWrite = containsEventType(args) || hasUuidAccessor(value, "eventId");
            var key = valueKey == null ? UUID.randomUUID() : valueKey;
            if (processedWrite) {
                processed.put(key, value);
                processedWrites++;
            } else {
                projections.put(key, value);
                projectionWrites++;
            }
            return method.getReturnType().equals(void.class) ? null : value;
        }

        private static boolean isPersistenceWrite(Method method, Object[] args) {
            if (args == null || args.length == 0) {
                return false;
            }
            if (containsEventType(args)) {
                return true;
            }
            if (args.length != 1 || args[0] == null || args[0] instanceof UUID) {
                return false;
            }
            var returnType = method.getReturnType();
            return returnType.equals(void.class)
                    || (!returnType.equals(boolean.class)
                    && !returnType.equals(Boolean.class)
                    && !returnType.equals(Optional.class)
                    && !returnType.isPrimitive());
        }

        private static boolean containsEventType(Object[] values) {
            if (values == null) {
                return false;
            }
            for (var value : values) {
                if (value instanceof com.suaposta.messaging.contract.EventType) {
                    return true;
                }
            }
            return false;
        }

        private static UUID findUuid(Object... values) {
            if (values == null) {
                return null;
            }
            for (var value : values) {
                if (value instanceof UUID uuid) {
                    return uuid;
                }
                if (value != null) {
                    for (var accessor : List.of("eventId", "getEventId", "betId", "getBetId")) {
                        try {
                            var method = value.getClass().getMethod(accessor);
                            var result = method.invoke(value);
                            if (result instanceof UUID uuid) {
                                return uuid;
                            }
                        } catch (ReflectiveOperationException ignored) {
                            // The test double only needs to recognize known UUID accessors.
                        }
                    }
                }
            }
            return null;
        }

        private static boolean hasUuidAccessor(Object value, String accessor) {
            if (value == null) {
                return false;
            }
            try {
                var result = value.getClass().getMethod(accessor).invoke(value);
                return result instanceof UUID;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                if (type.equals(Optional.class)) {
                    return Optional.empty();
                }
                if (Collection.class.isAssignableFrom(type)) {
                    return List.of();
                }
                return null;
            }
            if (type.equals(boolean.class)) {
                return false;
            }
            if (type.equals(int.class) || type.equals(long.class) || type.equals(short.class)
                    || type.equals(byte.class)) {
                return 0;
            }
            if (type.equals(double.class) || type.equals(float.class)) {
                return 0.0;
            }
            if (type.equals(char.class)) {
                return '\u0000';
            }
            return null;
        }
    }
}
