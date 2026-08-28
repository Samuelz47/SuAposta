package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.suaposta.analytics.application.model.AnalyticsBet;
import com.suaposta.messaging.contract.BetStatus;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Task73PerformanceBreakdownCalculationTest {

    private static final List<String> DECIMAL_METRICS = List.of(
            "totalStake", "profit", "roi", "yield", "winRate", "avgOdds", "drawdown");
    private static final List<String> COUNT_METRICS = List.of(
            "betsCount", "pendingCount", "wonCount", "lostCount", "voidCount", "cashoutCount", "cancelledCount");

    @Test
    void should_calculate_independent_bucket_metrics_from_projected_values() {
        var result = invokeBreakdown(List.of(
                bet("50000000-0000-0000-0000-000000000001", "LEAGUE_A", BetStatus.WON,
                        "100.00", "2.1000", "110.00", "2026-07-10T10:00:00Z"),
                bet("50000000-0000-0000-0000-000000000002", "LEAGUE_A", BetStatus.LOST,
                        "50.00", "2.0000", "-50.00", "2026-07-10T11:00:00Z"),
                bet("50000000-0000-0000-0000-000000000003", "LEAGUE_A", BetStatus.PENDING,
                        "90.00", "5.5000", null, null),
                bet("50000000-0000-0000-0000-000000000004", "LEAGUE_B", BetStatus.WON,
                        "400.00", "2.5000", "200.00", "2026-07-11T10:00:00Z"),
                bet("50000000-0000-0000-0000-000000000005", "LEAGUE_B", BetStatus.LOST,
                        "400.00", "1.5000", "-100.00", "2026-07-11T11:00:00Z")));

        var items = items(result);
        assertThat(items).hasSize(2);
        assertThat(read(items.get(0), "name")).isEqualTo("LEAGUE_A");
        assertDecimal(items.get(0), "totalStake", "150.00", 2);
        assertDecimal(items.get(0), "profit", "60.00", 2);
        assertDecimal(items.get(0), "roi", "40.00", 2);
        assertDecimal(items.get(0), "avgOdds", "2.0500", 4);
        assertCount(items.get(0), "betsCount", 3);
        assertCount(items.get(0), "pendingCount", 1);
        assertThat(read(items.get(1), "name")).isEqualTo("LEAGUE_B");
        assertDecimal(items.get(1), "totalStake", "800.00", 2);
        assertDecimal(items.get(1), "profit", "100.00", 2);
        assertDecimal(items.get(1), "roi", "12.50", 2);
        assertDecimal(items.get(1), "avgOdds", "2.0000", 4);
        assertCount(items.get(1), "betsCount", 2);
    }

    private static Method breakdownBoundary() {
        var candidates = Task63TestSupport.applicationClasses().stream()
                .flatMap(type -> Stream.of(type.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getReturnType() != void.class)
                .filter(method -> acceptsAuthenticatedUser(method.getParameterTypes()))
                .filter(method -> acceptsGroupBy(method.getParameterTypes()))
                .filter(method -> exposesItems(method.getReturnType()))
                .toList();
        assertThat(candidates)
                .as("Task 7.3 requires one neutral application boundary for authenticated grouped performance")
                .isNotEmpty();
        return candidates.stream()
                .sorted(Comparator.comparing(Method::toGenericString))
                .findFirst()
                .orElseThrow();
    }

    private static boolean acceptsAuthenticatedUser(Class<?>[] types) {
        return Stream.of(types).anyMatch(type -> type.equals(UUID.class) || containsComponent(type, UUID.class));
    }

    private static boolean acceptsGroupBy(Class<?>[] types) {
        return Stream.of(types).anyMatch(type -> type.equals(String.class)
                || type.isEnum()
                || (type.isRecord() && Stream.of(type.getRecordComponents())
                        .anyMatch(component -> component.getName().equalsIgnoreCase("groupBy"))));
    }

    private static boolean containsComponent(Class<?> type, Class<?> componentType) {
        return type.isRecord() && Stream.of(type.getRecordComponents())
                .anyMatch(component -> component.getType().equals(componentType));
    }

    private static boolean exposesItems(Class<?> type) {
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return true;
        }
        return accessor(type, "items") != null;
    }

    private static Object invokeBreakdown(List<AnalyticsBet> projections) {
        var boundary = breakdownBoundary();
        var target = constructSupported(boundary.getDeclaringClass(), projections, new HashSet<>());
        assertThat(target).as("application boundary collaborator graph").isNotNull();
        var arguments = Stream.of(boundary.getParameterTypes())
                .map(type -> applicationArgument(type))
                .toArray();
        try {
            boundary.setAccessible(true);
            return boundary.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Performance breakdown application boundary is not invokable", exception);
        } catch (InvocationTargetException exception) {
            var cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("Performance breakdown calculation failed", cause);
        }
    }

    private static Object constructSupported(
            Class<?> type, List<AnalyticsBet> projections, Set<Class<?>> constructionPath) {
        if (!constructionPath.add(type)) {
            return null;
        }
        for (var constructor : type.getDeclaredConstructors()) {
            var arguments = Stream.of(constructor.getParameterTypes())
                    .map(parameter -> dependency(parameter, projections, constructionPath))
                    .toArray();
            if (Stream.of(arguments).allMatch(argument -> argument != null)) {
                try {
                    constructor.setAccessible(true);
                    return constructor.newInstance(arguments);
                } catch (ReflectiveOperationException ignored) {
                    // Try another neutral constructor shape.
                }
            }
        }
        return null;
    }

    private static Object dependency(
            Class<?> type, List<AnalyticsBet> projections, Set<Class<?>> constructionPath) {
        if (type.equals(Clock.class)) {
            return Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC);
        }
        if (type.isInterface()) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                if (List.class.isAssignableFrom(method.getReturnType())
                        || Collection.class.isAssignableFrom(method.getReturnType())
                        || method.getReturnType().equals(Iterable.class)) {
                    return projections;
                }
                if (method.getReturnType().equals(Stream.class)) {
                    return projections.stream();
                }
                if (method.getReturnType().equals(Optional.class)) {
                    return Optional.empty();
                }
                if (method.getReturnType().equals(boolean.class)
                        || method.getReturnType().equals(Boolean.class)) {
                    return false;
                }
                return defaultValue(method.getReturnType());
            });
        }
        var packageName = type.getPackageName();
        if (!Modifier.isAbstract(type.getModifiers())
                && (packageName.startsWith("com.suaposta.analytics.application")
                || packageName.startsWith("com.suaposta.analytics.domain"))) {
            return constructSupported(type, projections, new HashSet<>(constructionPath));
        }
        return null;
    }

    private static Object applicationArgument(Class<?> type) {
        if (type.equals(UUID.class)) {
            return Task73TestSupport.USER_A;
        }
        if (type.equals(String.class) || type.isEnum()) {
            return groupByArgument(type);
        }
        if (type.isRecord()) {
            try {
                var components = type.getRecordComponents();
                var values = Stream.of(components).map(component -> {
                    if (component.getType().equals(UUID.class)) {
                        return Task73TestSupport.USER_A;
                    }
                    if (component.getName().equalsIgnoreCase("groupBy")) {
                        return groupByArgument(component.getType());
                    }
                    return defaultValue(component.getType());
                }).toArray();
                var parameterTypes = Stream.of(components).map(component -> component.getType()).toArray(Class[]::new);
                var constructor = type.getDeclaredConstructor(parameterTypes);
                constructor.setAccessible(true);
                return constructor.newInstance(values);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Performance breakdown query could not be constructed", exception);
            }
        }
        return defaultValue(type);
    }

    private static Object groupByArgument(Class<?> type) {
        if (type.equals(String.class)) {
            return "LEAGUE";
        }
        if (type.isEnum()) {
            return Stream.of(type.getEnumConstants())
                    .filter(constant -> ((Enum<?>) constant).name().equals("LEAGUE"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "groupBy enum must expose the documented LEAGUE value"));
        }
        throw new AssertionError("Unsupported groupBy argument type: " + type.getName());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(char.class)) {
            return '\u0000';
        }
        return 0;
    }

    private static List<?> items(Object result) {
        assertThat(result).as("performance breakdown application result").isNotNull();
        if (result instanceof List<?> list) {
            return list;
        }
        var accessor = accessor(result.getClass(), "items");
        assertThat(accessor).as("application result must expose items").isNotNull();
        try {
            var value = accessor.invoke(result);
            assertThat(value).as("performance breakdown items value").isInstanceOf(Collection.class);
            return ((Collection<?>) value).stream().toList();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("performance breakdown items accessor failed", exception);
        }
    }

    private static Method accessor(Class<?> type, String name) {
        for (var candidate : List.of(name, "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1))) {
            try {
                return type.getMethod(candidate);
            } catch (NoSuchMethodException ignored) {
                // Accept record-style and bean-style accessors.
            }
        }
        return null;
    }

    private static Object read(Object target, String name) {
        var method = accessor(target.getClass(), name);
        assertThat(method).as("item must expose " + name).isNotNull();
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("item accessor failed: " + name, exception);
        }
    }

    private static void assertDecimal(Object item, String name, String expected, int scale) {
        var value = read(item, name);
        assertThat(value).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) value).isEqualByComparingTo(expected);
        assertThat(((BigDecimal) value).scale()).isEqualTo(scale);
    }

    private static void assertCount(Object item, String name, int expected) {
        var value = read(item, name);
        assertThat(value).isInstanceOf(Number.class);
        assertThat(((Number) value).longValue()).isEqualTo(expected);
    }

    private static AnalyticsBet bet(
            String betId, String league, BetStatus status, String stake, String odds,
            String profit, String settledAt) {
        var id = UUID.fromString(betId);
        var placed = Instant.parse("2026-07-01T10:00:00Z");
        var settled = settledAt == null ? null : Instant.parse(settledAt);
        return new AnalyticsBet(
                id, id, Task73TestSupport.USER_A, "FOOTBALL", league, "HOME", "AWAY", "MARKET", "SELECTION",
                new BigDecimal(odds), new BigDecimal(stake), status,
                profit == null ? null : new BigDecimal(profit),
                profit == null ? null : new BigDecimal(stake).add(new BigDecimal(profit)),
                placed, settled, placed, settled == null ? placed : settled);
    }
}
