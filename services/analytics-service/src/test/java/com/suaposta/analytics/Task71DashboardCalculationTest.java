package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.suaposta.analytics.application.model.AnalyticsBet;
import com.suaposta.messaging.contract.BetStatus;
import java.lang.reflect.Constructor;
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

class Task71DashboardCalculationTest {

    private static final List<String> DECIMAL_METRICS = List.of(
            "totalStake", "totalProfit", "roi", "yield", "winRate", "averageOdds",
            "maxDrawdown", "currentDrawdown");
    private static final List<String> COUNT_METRICS = List.of(
            "betsCount", "wonBets", "lostBets", "voidBets", "cashoutBets", "cancelledBets");

    @Test
    void should_calculate_mixed_metrics_from_projected_values_through_an_application_boundary() {
        var result = invokeDashboard(List.of(
                bet("10000000-0000-0000-0000-000000000101", BetStatus.WON, "100.00", "2.1000", "110.00", 1),
                bet("10000000-0000-0000-0000-000000000102", BetStatus.LOST, "50.00", "2.0000", "-50.00", 2),
                bet("10000000-0000-0000-0000-000000000103", BetStatus.CASHOUT, "100.00", "1.8000", "-20.00", 3),
                bet("10000000-0000-0000-0000-000000000104", BetStatus.VOID, "70.00", "3.5000", "0.00", 4),
                bet("10000000-0000-0000-0000-000000000105", BetStatus.CANCELLED, "80.00", "4.5000", "0.00", 5),
                pending("10000000-0000-0000-0000-000000000106", "90.00", "5.5000")));

        assertDecimal(result, "totalStake", "250.00", 2);
        assertDecimal(result, "totalProfit", "40.00", 2);
        assertDecimal(result, "roi", "16.00", 2);
        assertDecimal(result, "yield", "16.00", 2);
        assertDecimal(result, "winRate", "50.00", 2);
        assertDecimal(result, "averageOdds", "1.9667", 4);
        assertCount(result, "betsCount", 6);
        assertCount(result, "wonBets", 1);
        assertCount(result, "lostBets", 1);
        assertCount(result, "voidBets", 1);
        assertCount(result, "cashoutBets", 1);
        assertCount(result, "cancelledBets", 1);
        assertDecimal(result, "maxDrawdown", "70.00", 2);
        assertDecimal(result, "currentDrawdown", "70.00", 2);
    }

    @Test
    void should_preserve_negative_projected_cashout_performance_and_zero_non_cashout_denominators() {
        var result = invokeDashboard(List.of(
                bet("10000000-0000-0000-0000-000000000111", BetStatus.CASHOUT,
                        "120.13", "9.9999", "-19.87", 1),
                pending("10000000-0000-0000-0000-000000000112", "80.13", "2.1256")));

        assertDecimal(result, "totalProfit", "-19.87", 2);
        assertDecimal(result, "roi", "-16.54", 2);
        assertDecimal(result, "yield", "-16.54", 2);
        assertDecimal(result, "winRate", "0.00", 2);
        assertDecimal(result, "averageOdds", "9.9999", 4);
        assertDecimal(result, "maxDrawdown", "19.87", 2);
        assertDecimal(result, "currentDrawdown", "19.87", 2);
    }

    @Test
    void should_return_scaled_zero_values_for_empty_application_input() {
        var result = invokeDashboard(List.of());

        for (var metric : DECIMAL_METRICS) {
            assertDecimal(result, metric, metric.equals("averageOdds") ? "0.0000" : "0.00",
                    metric.equals("averageOdds") ? 4 : 2);
        }
        for (var metric : COUNT_METRICS) {
            assertCount(result, metric, 0);
        }
    }

    @Test
    void should_order_drawdown_by_settled_at_then_bet_id_and_round_only_at_final_boundaries() {
        var shared = Instant.parse("2026-07-01T10:00:00Z");
        var first = bet("10000000-0000-0000-0000-000000000121", BetStatus.WON,
                "100.00", "2.1256", "100.00", shared);
        var second = bet("10000000-0000-0000-0000-000000000122", BetStatus.LOST,
                "50.00", "1.1111", "-50.00", shared);
        var third = bet("10000000-0000-0000-0000-000000000123", BetStatus.WON,
                "9.00", "1.1112", "1.00", Instant.parse("2026-07-01T11:00:00Z"));

        var result = invokeDashboard(List.of(third, second, first));

        assertDecimal(result, "roi", "32.08", 2);
        assertDecimal(result, "winRate", "66.67", 2);
        assertDecimal(result, "averageOdds", "1.4493", 4);
        assertDecimal(result, "maxDrawdown", "50.00", 2);
        assertDecimal(result, "currentDrawdown", "49.00", 2);
    }

    private static Object invokeDashboard(List<AnalyticsBet> projections) {
        var boundary = dashboardBoundary();
        var target = construct(boundary.getDeclaringClass(), projections);
        var arguments = Stream.of(boundary.getParameterTypes())
                .map(Task71DashboardCalculationTest::applicationArgument)
                .toArray();
        try {
            boundary.setAccessible(true);
            return boundary.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Dashboard application boundary is not invokable", exception);
        } catch (InvocationTargetException exception) {
            var cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("Dashboard application calculation failed", cause);
        }
    }

    private static Method dashboardBoundary() {
        var candidates = Task63TestSupport.applicationClasses().stream()
                .flatMap(type -> Stream.of(type.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getReturnType() != void.class)
                .filter(method -> acceptsAuthenticatedUser(method.getParameterTypes()))
                .filter(method -> exposesMetricShape(method.getReturnType()))
                .toList();
        assertThat(candidates)
                .as("Task 7.1 requires one identifiable application boundary shaped as authenticated user + filters -> dashboard summary")
                .hasSize(1);
        return candidates.get(0);
    }

    private static boolean acceptsAuthenticatedUser(Class<?>[] parameterTypes) {
        return Stream.of(parameterTypes).anyMatch(type -> type.equals(UUID.class) || containsUuid(type));
    }

    private static boolean containsUuid(Class<?> type) {
        if (type.isRecord()) {
            return Stream.of(type.getRecordComponents()).anyMatch(component -> component.getType().equals(UUID.class));
        }
        return Stream.of(type.getMethods())
                .filter(method -> method.getParameterCount() == 0)
                .anyMatch(method -> method.getReturnType().equals(UUID.class));
    }

    private static boolean exposesMetricShape(Class<?> type) {
        if (hasAccessors(type, DECIMAL_METRICS) && hasAccessors(type, COUNT_METRICS)) {
            return true;
        }
        return Stream.of(type.getMethods())
                .filter(method -> method.getParameterCount() == 0)
                .map(Method::getReturnType)
                .anyMatch(nested -> hasAccessors(nested, DECIMAL_METRICS) && hasAccessors(nested, COUNT_METRICS));
    }

    private static boolean hasAccessors(Class<?> type, List<String> names) {
        var methods = Stream.of(type.getMethods()).map(Method::getName).collect(java.util.stream.Collectors.toSet());
        return names.stream().allMatch(name -> methods.contains(name) || methods.contains(getter(name)));
    }

    private static Object construct(Class<?> type, List<AnalyticsBet> projections) {
        var result = constructSupported(type, projections, new HashSet<>());
        if (result instanceof Unsupported) {
            throw new AssertionError(
                    "Dashboard application boundary has no safely constructible application/domain collaborator graph");
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
            return Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC);
        }
        if (type.isInterface()) {
            return persistencePortDouble(type, projections);
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

    private static Object persistencePortDouble(Class<?> type, List<AnalyticsBet> projections) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            var returnType = method.getReturnType();
            if (returnType.equals(List.class)
                    || returnType.equals(Collection.class)
                    || returnType.equals(Iterable.class)) {
                return projections;
            }
            if (returnType.equals(Stream.class)) {
                return projections.stream();
            }
            if (returnType.equals(Optional.class)) {
                return Optional.empty();
            }
            if (returnType.equals(boolean.class) || returnType.equals(Boolean.class)) {
                return false;
            }
            if (returnType.isPrimitive()) {
                return 0;
            }
            return null;
        });
    }

    private static Object applicationArgument(Class<?> type) {
        if (type.equals(UUID.class)) {
            return Task71TestSupport.USER_A;
        }
        if (type.isRecord()) {
            try {
                var components = type.getRecordComponents();
                var arguments = Stream.of(components)
                        .map(component -> component.getType().equals(UUID.class)
                                ? Task71TestSupport.USER_A
                                : defaultValue(component.getType()))
                        .toArray();
                var parameterTypes = Stream.of(components).map(component -> component.getType()).toArray(Class[]::new);
                var constructor = type.getDeclaredConstructor(parameterTypes);
                constructor.setAccessible(true);
                return constructor.newInstance(arguments);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Dashboard query/filter record could not be constructed", exception);
            }
        }
        if (type.isInterface()) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                    (proxy, method, args) -> method.getReturnType().equals(UUID.class)
                            ? Task71TestSupport.USER_A
                            : defaultValue(method.getReturnType()));
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(char.class)) {
            return '\0';
        }
        return 0;
    }

    private static void assertDecimal(Object result, String metric, String expected, int scale) {
        var value = readMetric(result, metric);
        assertThat(value).as(metric + " must use BigDecimal").isInstanceOf(BigDecimal.class);
        var decimal = (BigDecimal) value;
        assertThat(decimal).isEqualByComparingTo(expected);
        assertThat(decimal.scale()).as(metric + " scale").isEqualTo(scale);
    }

    private static void assertCount(Object result, String metric, int expected) {
        var value = readMetric(result, metric);
        assertThat(value).as(metric + " must be an integer count").isInstanceOfAny(Integer.class, Long.class);
        assertThat(((Number) value).longValue()).isEqualTo(expected);
    }

    private static Object readMetric(Object result, String metric) {
        assertThat(result).as("dashboard application result").isNotNull();
        var direct = accessor(result.getClass(), metric);
        if (direct != null) {
            return invokeAccessor(result, direct);
        }
        for (var method : result.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && !method.getReturnType().isPrimitive()) {
                var nestedAccessor = accessor(method.getReturnType(), metric);
                if (nestedAccessor != null) {
                    return invokeAccessor(invokeAccessor(result, method), nestedAccessor);
                }
            }
        }
        throw new AssertionError("Dashboard result does not expose contractual metric " + metric);
    }

    private static Method accessor(Class<?> type, String name) {
        for (var candidate : List.of(name, getter(name))) {
            try {
                return type.getMethod(candidate);
            } catch (NoSuchMethodException ignored) {
                // Both record-style and JavaBean-style result accessors are accepted.
            }
        }
        return null;
    }

    private static Object invokeAccessor(Object target, Method method) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Dashboard result accessor failed: " + method.getName(), exception);
        }
    }

    private static String getter(String name) {
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static AnalyticsBet bet(
            String betId, BetStatus status, String stake, String odds, String profit, int hour) {
        return bet(betId, status, stake, odds, profit, Instant.parse("2026-07-01T0" + hour + ":00:00Z"));
    }

    private static AnalyticsBet bet(
            String betId, BetStatus status, String stake, String odds, String profit, Instant settledAt) {
        var id = UUID.fromString(betId);
        var placedAt = Instant.parse("2026-06-30T10:00:00Z");
        return new AnalyticsBet(
                id, id, Task71TestSupport.USER_A, "FOOTBALL", "League", "Home", "Away", "MARKET", "Home",
                new BigDecimal(odds), new BigDecimal(stake), status, new BigDecimal(profit),
                new BigDecimal(stake).add(new BigDecimal(profit)), placedAt, settledAt, placedAt, settledAt);
    }

    private static AnalyticsBet pending(String betId, String stake, String odds) {
        var id = UUID.fromString(betId);
        var placedAt = Instant.parse("2026-06-30T10:00:00Z");
        return new AnalyticsBet(
                id, id, Task71TestSupport.USER_A, "FOOTBALL", "League", "Home", "Away", "MARKET", "Home",
                new BigDecimal(odds), new BigDecimal(stake), BetStatus.PENDING, null, null,
                placedAt, null, placedAt, placedAt);
    }

    private static final class Unsupported {
    }
}
