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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Task72BankrollEvolutionCalculationTest {

    @Test
    void should_calculate_one_ordered_point_per_eligible_projection_from_zero_baseline() {
        var sameInstant = Instant.parse("2026-07-01T10:00:00Z");
        var first = bet("10000000-0000-0000-0000-000000000101", BetStatus.WON,
                "100.00", "2.0000", "7.25", sameInstant);
        var second = bet("10000000-0000-0000-0000-000000000102", BetStatus.LOST,
                "5.00", "9.9999", "-12.50", sameInstant);
        var third = bet("10000000-0000-0000-0000-000000000103", BetStatus.CASHOUT,
                "1.00", "1.0100", "0.00", Instant.parse("2026-07-01T11:00:00Z"));
        var result = invokeEvolution(List.of(
                bet("10000000-0000-0000-0000-000000000104", BetStatus.VOID,
                        "70.00", "3.5000", "0.00", Instant.parse("2026-07-01T09:00:00Z")),
                pending("10000000-0000-0000-0000-000000000105"),
                bet("10000000-0000-0000-0000-000000000106", BetStatus.CANCELLED,
                        "80.00", "4.5000", "0.00", Instant.parse("2026-07-01T12:00:00Z")),
                third, second, first));

        var points = points(result);
        assertThat(points).hasSize(3);
        assertPoint(points.get(0), "2026-07-01", "7.25", "7.25", "7.25");
        assertPoint(points.get(1), "2026-07-01", "-12.50", "-5.25", "-5.25");
        assertPoint(points.get(2), "2026-07-01", "0.00", "-5.25", "-5.25");
    }

    @Test
    void should_use_persisted_profit_and_preserve_negative_and_zero_cumulative_values() {
        var result = invokeEvolution(List.of(
                bet("10000000-0000-0000-0000-000000000111", BetStatus.WON,
                        "100.00", "9.9999", "-10.00", Instant.parse("2026-07-02T10:00:00Z")),
                bet("10000000-0000-0000-0000-000000000112", BetStatus.LOST,
                        "100.00", "1.0100", "5.25", Instant.parse("2026-07-02T11:00:00Z")),
                bet("10000000-0000-0000-0000-000000000113", BetStatus.CASHOUT,
                        "100.00", "2.0000", "0.00", Instant.parse("2026-07-02T12:00:00Z"))));

        var points = points(result);
        assertThat(points).hasSize(3);
        assertPoint(points.get(0), "2026-07-02", "-10.00", "-10.00", "-10.00");
        assertPoint(points.get(1), "2026-07-02", "5.25", "-4.75", "-4.75");
        assertPoint(points.get(2), "2026-07-02", "0.00", "-4.75", "-4.75");
    }

    private static Method evolutionBoundary() {
        var candidates = Task63TestSupport.applicationClasses().stream()
                .flatMap(type -> Stream.of(type.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.getDeclaringClass().isInterface())
                .filter(method -> acceptsAuthenticatedUser(method.getParameterTypes()))
                .filter(method -> exposesPointShape(method.getReturnType()))
                .toList();
        assertThat(candidates)
                .as("Task 7.2 requires one identifiable application boundary shaped as authenticated user + filters -> evolution")
                .hasSize(1);
        return candidates.get(0);
    }

    private static boolean acceptsAuthenticatedUser(Class<?>[] parameterTypes) {
        return Stream.of(parameterTypes).anyMatch(type -> type.equals(UUID.class) || containsUuid(type));
    }

    private static boolean containsUuid(Class<?> type) {
        if (type.isRecord()) {
            return Stream.of(type.getRecordComponents())
                    .anyMatch(component -> component.getType().equals(UUID.class));
        }
        return Stream.of(type.getMethods())
                .filter(method -> method.getParameterCount() == 0)
                .anyMatch(method -> method.getReturnType().equals(UUID.class));
    }

    private static boolean exposesPointShape(Class<?> type) {
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return true;
        }
        return accessor(type, "points") != null;
    }

    private static Object invokeEvolution(List<AnalyticsBet> projections) {
        var boundary = evolutionBoundary();
        var target = construct(boundary.getDeclaringClass(), projections);
        var arguments = Stream.of(boundary.getParameterTypes())
                .map(type -> applicationArgument(type))
                .toArray();
        try {
            boundary.setAccessible(true);
            return boundary.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Bankroll evolution application boundary is not invokable", exception);
        } catch (InvocationTargetException exception) {
            var cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("Bankroll evolution calculation failed", cause);
        }
    }

    private static Object construct(Class<?> type, List<AnalyticsBet> projections) {
        var result = constructSupported(type, projections, new HashSet<>());
        if (result instanceof Unsupported) {
            throw new AssertionError(
                    "Bankroll evolution application boundary has no safely constructible application/domain collaborator graph");
        }
        return result;
    }

    private static Object constructSupported(
            Class<?> type, List<AnalyticsBet> projections, Set<Class<?>> constructionPath) {
        if (!constructionPath.add(type)) {
            return new Unsupported();
        }
        for (var constructor : type.getDeclaredConstructors()) {
            var arguments = Stream.of(constructor.getParameterTypes())
                    .map(parameter -> dependency(parameter, projections, constructionPath))
                    .toArray();
            if (Stream.of(arguments).noneMatch(Unsupported.class::isInstance)) {
                try {
                    constructor.setAccessible(true);
                    return constructor.newInstance(arguments);
                } catch (ReflectiveOperationException ignored) {
                    // Try another safe constructor shape before declaring this collaborator unsupported.
                }
            }
        }
        return new Unsupported();
    }

    private static Object dependency(
            Class<?> type, List<AnalyticsBet> projections, Set<Class<?>> constructionPath) {
        if (type.equals(Clock.class)) {
            return Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC);
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
                if (method.getReturnType().isPrimitive()) {
                    return 0;
                }
                return defaultValue(method.getReturnType());
            });
        }
        var packageName = type.getPackageName();
        var safeConcreteCollaborator = !Modifier.isAbstract(type.getModifiers())
                && (packageName.startsWith("com.suaposta.analytics.application")
                || packageName.startsWith("com.suaposta.analytics.domain"));
        if (safeConcreteCollaborator) {
            var branch = new HashSet<>(constructionPath);
            return constructSupported(type, projections, branch);
        }
        return new Unsupported();
    }

    private static Object applicationArgument(Class<?> type) {
        if (type.equals(UUID.class)) {
            return Task72TestSupport.USER_A;
        }
        if (type.isRecord()) {
            try {
                var components = type.getRecordComponents();
                var parameterTypes = Stream.of(components).map(component -> component.getType()).toArray(Class[]::new);
                var values = Stream.of(components)
                        .map(component -> component.getType().equals(UUID.class)
                                ? Task72TestSupport.USER_A : defaultValue(component.getType()))
                        .toArray();
                var constructor = type.getDeclaredConstructor(parameterTypes);
                constructor.setAccessible(true);
                return constructor.newInstance(values);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Bankroll evolution filter could not be constructed", exception);
            }
        }
        if (type.isInterface()) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                    (proxy, method, args) -> method.getReturnType().equals(UUID.class)
                            ? Task72TestSupport.USER_A
                            : defaultValue(method.getReturnType()));
        }
        if (containsUuid(type)) {
            for (var constructor : type.getDeclaredConstructors()) {
                var arguments = Stream.of(constructor.getParameterTypes())
                        .map(parameter -> parameter.equals(UUID.class)
                                ? Task72TestSupport.USER_A : defaultValue(parameter))
                        .toArray();
                try {
                    constructor.setAccessible(true);
                    return constructor.newInstance(arguments);
                } catch (ReflectiveOperationException ignored) {
                    // Try another neutral query-object constructor shape.
                }
            }
        }
        return defaultValue(type);
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

    private static List<?> points(Object result) {
        assertThat(result).as("bankroll evolution application result").isNotNull();
        if (result instanceof List<?> list) {
            return list;
        }
        var accessor = accessor(result.getClass(), "points");
        assertThat(accessor).as("application result must expose points").isNotNull();
        try {
            return (List<?>) accessor.invoke(result);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("bankroll evolution points accessor failed", exception);
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

    private static void assertPoint(Object point, String date, String profit, String cumulative, String bankroll) {
        assertThat(read(point, "date", "settledAt", "timestamp").toString()).contains(date);
        assertThat(readDecimal(point, "profit")).isEqualByComparingTo(profit);
        assertThat(readDecimal(point, "cumulativeProfit")).isEqualByComparingTo(cumulative);
        assertThat(readDecimal(point, "bankroll")).isEqualByComparingTo(bankroll);
    }

    private static Object read(Object target, String... names) {
        for (var name : names) {
            var method = accessor(target.getClass(), name);
            if (method != null) {
                try {
                    return method.invoke(target);
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError("point accessor failed: " + name, exception);
                }
            }
        }
        throw new AssertionError("point does not expose a documented date/timestamp accessor");
    }

    private static BigDecimal readDecimal(Object target, String name) {
        return (BigDecimal) read(target, name);
    }

    private static AnalyticsBet bet(
            String betId, BetStatus status, String stake, String odds, String profit, Instant settledAt) {
        var id = UUID.fromString(betId);
        var placedAt = Instant.parse("2026-06-30T10:00:00Z");
        return new AnalyticsBet(
                id, id, Task72TestSupport.USER_A, "FOOTBALL", "League", "Home", "Away", "MARKET", "Selection",
                new BigDecimal(odds), new BigDecimal(stake), status, new BigDecimal(profit), new BigDecimal("0.00"),
                placedAt, settledAt, placedAt, settledAt);
    }

    private static AnalyticsBet pending(String betId) {
        var id = UUID.fromString(betId);
        var placedAt = Instant.parse("2026-06-30T10:00:00Z");
        return new AnalyticsBet(
                id, id, Task72TestSupport.USER_A, "FOOTBALL", "League", "Home", "Away", "MARKET", "Selection",
                new BigDecimal("2.0000"), new BigDecimal("100.00"), BetStatus.PENDING, null, null,
                placedAt, null, placedAt, placedAt);
    }

    private static final class Unsupported {
    }
}
