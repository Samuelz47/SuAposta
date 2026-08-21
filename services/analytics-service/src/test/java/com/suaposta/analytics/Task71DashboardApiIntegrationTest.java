package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Task71DashboardApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16.4-alpine"));

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startApplication() {
        context = Task71TestSupport.startApplication(POSTGRES);
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        Task71TestSupport.resetDatabase(POSTGRES);
    }

    @Test
    void should_return_the_exact_mixed_summary_for_only_the_authenticated_user() throws Exception {
        Task71TestSupport.insertMixedFixture(POSTGRES);

        var response = dashboard(Task71TestSupport.USER_A, "");

        assertThat(response.statusCode()).isEqualTo(200);
        var root = Task71TestSupport.body(response);
        Task71TestSupport.assertExactFields(root, java.util.Set.of("summary", "filters"));
        var summary = root.path("summary");
        Task71TestSupport.assertExactFields(summary, Task71TestSupport.SUMMARY_FIELDS);
        Task71TestSupport.assertMetric(summary, "totalStake", "250.00", 2);
        Task71TestSupport.assertMetric(summary, "totalProfit", "40.00", 2);
        Task71TestSupport.assertMetric(summary, "roi", "16.00", 2);
        Task71TestSupport.assertMetric(summary, "yield", "16.00", 2);
        Task71TestSupport.assertMetric(summary, "winRate", "50.00", 2);
        Task71TestSupport.assertMetric(summary, "averageOdds", "1.9667", 4);
        Task71TestSupport.assertCount(summary, "betsCount", 6);
        Task71TestSupport.assertCount(summary, "wonBets", 1);
        Task71TestSupport.assertCount(summary, "lostBets", 1);
        Task71TestSupport.assertCount(summary, "voidBets", 1);
        Task71TestSupport.assertCount(summary, "cashoutBets", 1);
        Task71TestSupport.assertCount(summary, "cancelledBets", 1);
        Task71TestSupport.assertMetric(summary, "maxDrawdown", "70.00", 2);
        Task71TestSupport.assertMetric(summary, "currentDrawdown", "70.00", 2);
        assertAllFiltersNull(root.path("filters"));
        assertThat(response.body()).doesNotContain(
                Task71TestSupport.USER_A.toString(), Task71TestSupport.USER_B.toString(),
                "\"betId\"", "\"userId\"", "\"id\"");
    }

    @Test
    void should_return_the_complete_scaled_empty_summary_and_all_null_filters() throws Exception {
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                "20000000-0000-0000-0000-000000000010", Task71TestSupport.USER_B, "WON",
                "100.00", "2.0000", "100.00", "200.00",
                "2026-07-01T10:00:00Z", "2026-07-01T11:00:00Z"));

        var response = dashboard(Task71TestSupport.USER_A, "");

        assertThat(response.statusCode()).isEqualTo(200);
        var root = Task71TestSupport.body(response);
        Task71TestSupport.assertExactFields(root, java.util.Set.of("summary", "filters"));
        var summary = root.path("summary");
        Task71TestSupport.assertExactFields(summary, Task71TestSupport.SUMMARY_FIELDS);
        for (var field : java.util.List.of(
                "totalStake", "totalProfit", "roi", "yield", "winRate", "maxDrawdown", "currentDrawdown")) {
            Task71TestSupport.assertMetric(summary, field, "0.00", 2);
        }
        Task71TestSupport.assertMetric(summary, "averageOdds", "0.0000", 4);
        for (var field : java.util.List.of(
                "betsCount", "wonBets", "lostBets", "voidBets", "cashoutBets", "cancelledBets")) {
            Task71TestSupport.assertCount(summary, field, 0);
        }
        assertAllFiltersNull(root.path("filters"));
    }

    @Test
    void should_require_a_valid_trusted_user_identity() throws Exception {
        Task71TestSupport.assertSafeError(
                Task71TestSupport.dashboard(context, null, ""), 401);
        Task71TestSupport.assertSafeError(
                Task71TestSupport.dashboard(context, "not-a-uuid", ""), 401);
    }

    @Test
    void should_keep_pending_in_bets_count_but_out_of_every_performance_metric() throws Exception {
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000020", Task71TestSupport.USER_A, "PENDING",
                "120.13", "2.1256", null, null,
                "2026-07-01T10:00:00Z", null));

        var response = dashboard(Task71TestSupport.USER_A, "?status=PENDING");

        assertThat(response.statusCode()).isEqualTo(200);
        var root = Task71TestSupport.body(response);
        var summary = root.path("summary");
        Task71TestSupport.assertCount(summary, "betsCount", 1);
        for (var field : java.util.List.of(
                "totalStake", "totalProfit", "roi", "yield", "winRate", "maxDrawdown", "currentDrawdown")) {
            Task71TestSupport.assertMetric(summary, field, "0.00", 2);
        }
        Task71TestSupport.assertMetric(summary, "averageOdds", "0.0000", 4);
        Task71TestSupport.assertCount(summary, "wonBets", 0);
        Task71TestSupport.assertCount(summary, "lostBets", 0);
        Task71TestSupport.assertCount(summary, "voidBets", 0);
        Task71TestSupport.assertCount(summary, "cashoutBets", 0);
        Task71TestSupport.assertCount(summary, "cancelledBets", 0);
        assertThat(root.path("filters").path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void should_use_projected_cashout_profit_and_exclude_cashout_from_win_rate() throws Exception {
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000030", Task71TestSupport.USER_A, "CASHOUT",
                "120.13", "9.9999", "-19.87", "88.88",
                "2026-07-01T10:00:00Z", "2026-07-01T11:00:00Z"));

        var response = dashboard(Task71TestSupport.USER_A, "");

        assertThat(response.statusCode()).isEqualTo(200);
        var summary = Task71TestSupport.body(response).path("summary");
        Task71TestSupport.assertMetric(summary, "totalStake", "120.13", 2);
        Task71TestSupport.assertMetric(summary, "totalProfit", "-19.87", 2);
        Task71TestSupport.assertMetric(summary, "roi", "-16.54", 2);
        Task71TestSupport.assertMetric(summary, "yield", "-16.54", 2);
        Task71TestSupport.assertMetric(summary, "winRate", "0.00", 2);
        Task71TestSupport.assertMetric(summary, "averageOdds", "9.9999", 4);
        Task71TestSupport.assertMetric(summary, "maxDrawdown", "19.87", 2);
        Task71TestSupport.assertMetric(summary, "currentDrawdown", "19.87", 2);
        Task71TestSupport.assertCount(summary, "cashoutBets", 1);
    }

    @Test
    void should_round_only_final_percentage_and_average_results_with_half_up() throws Exception {
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000041", Task71TestSupport.USER_A, "WON",
                "100.00", "2.0100", "101.00", "201.00",
                "2026-07-01T10:00:00Z", "2026-07-01T11:00:00Z"));
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000042", Task71TestSupport.USER_A, "LOST",
                "50.00", "1.1111", "-50.00", "0.00",
                "2026-07-02T10:00:00Z", "2026-07-02T11:00:00Z"));
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000043", Task71TestSupport.USER_A, "LOST",
                "50.00", "1.1112", "-50.00", "0.00",
                "2026-07-03T10:00:00Z", "2026-07-03T11:00:00Z"));

        var response = dashboard(Task71TestSupport.USER_A, "");

        assertThat(response.statusCode()).isEqualTo(200);
        var summary = Task71TestSupport.body(response).path("summary");
        Task71TestSupport.assertMetric(summary, "totalStake", "200.00", 2);
        Task71TestSupport.assertMetric(summary, "totalProfit", "1.00", 2);
        Task71TestSupport.assertMetric(summary, "roi", "0.50", 2);
        Task71TestSupport.assertMetric(summary, "yield", "0.50", 2);
        Task71TestSupport.assertMetric(summary, "winRate", "33.33", 2);
        Task71TestSupport.assertMetric(summary, "averageOdds", "1.4108", 4);
    }

    @Test
    void should_calculate_drawdown_chronologically_with_recovery_and_ineligible_statuses_ignored() throws Exception {
        insertDrawdown("10000000-0000-0000-0000-000000000051", "WON", "100.00", "2026-07-01T10:00:00Z");
        insertDrawdown("10000000-0000-0000-0000-000000000052", "LOST", "-80.00", "2026-07-01T11:00:00Z");
        insertDrawdown("10000000-0000-0000-0000-000000000053", "CASHOUT", "60.00", "2026-07-01T12:00:00Z");
        insertDrawdown("10000000-0000-0000-0000-000000000054", "LOST", "-20.00", "2026-07-01T13:00:00Z");
        insertDrawdown("10000000-0000-0000-0000-000000000055", "VOID", "0.00", "2026-07-01T14:00:00Z");
        insertDrawdown("10000000-0000-0000-0000-000000000056", "CANCELLED", "0.00", "2026-07-01T15:00:00Z");
        var pending = Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000057", Task71TestSupport.USER_A, "PENDING",
                "10.00", "2.0000", null, null, "2026-07-01T09:00:00Z", null);
        Task71TestSupport.insertBet(POSTGRES, pending);

        var response = dashboard(Task71TestSupport.USER_A, "");

        assertThat(response.statusCode()).isEqualTo(200);
        var summary = Task71TestSupport.body(response).path("summary");
        Task71TestSupport.assertMetric(summary, "maxDrawdown", "80.00", 2);
        Task71TestSupport.assertMetric(summary, "currentDrawdown", "40.00", 2);
    }

    @Test
    void should_use_bet_id_as_drawdown_tie_breaker_for_equal_settlement_instants() throws Exception {
        insertDrawdown("10000000-0000-0000-0000-000000000061", "WON", "100.00", "2026-07-01T10:00:00Z");
        insertDrawdown("10000000-0000-0000-0000-000000000062", "LOST", "-50.00", "2026-07-01T10:00:00Z");

        var response = dashboard(Task71TestSupport.USER_A, "");

        assertThat(response.statusCode()).isEqualTo(200);
        var summary = Task71TestSupport.body(response).path("summary");
        Task71TestSupport.assertMetric(summary, "maxDrawdown", "50.00", 2);
        Task71TestSupport.assertMetric(summary, "currentDrawdown", "50.00", 2);
    }

    @Test
    void should_return_zero_drawdown_for_positive_sequence_and_after_full_recovery() throws Exception {
        insertDrawdown("10000000-0000-0000-0000-000000000071", "WON", "10.00", "2026-07-01T10:00:00Z");
        insertDrawdown("10000000-0000-0000-0000-000000000072", "WON", "20.00", "2026-07-01T11:00:00Z");
        var positive = dashboard(Task71TestSupport.USER_A, "");
        assertThat(positive.statusCode()).isEqualTo(200);
        Task71TestSupport.assertMetric(Task71TestSupport.body(positive).path("summary"), "maxDrawdown", "0.00", 2);
        Task71TestSupport.resetDatabase(POSTGRES);
        insertDrawdown("10000000-0000-0000-0000-000000000073", "WON", "100.00", "2026-07-01T10:00:00Z");
        insertDrawdown("10000000-0000-0000-0000-000000000074", "LOST", "-80.00", "2026-07-01T11:00:00Z");
        insertDrawdown("10000000-0000-0000-0000-000000000075", "WON", "100.00", "2026-07-01T12:00:00Z");

        var recovered = dashboard(Task71TestSupport.USER_A, "");

        assertThat(recovered.statusCode()).isEqualTo(200);
        var summary = Task71TestSupport.body(recovered).path("summary");
        Task71TestSupport.assertMetric(summary, "maxDrawdown", "80.00", 2);
        Task71TestSupport.assertMetric(summary, "currentDrawdown", "0.00", 2);
    }

    @Test
    void should_filter_dates_by_inclusive_placed_at_not_settled_at_and_echo_effective_instants() throws Exception {
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000081", Task71TestSupport.USER_A, "WON",
                "10.00", "2.0000", "10.00", "20.00",
                "2026-07-02T10:00:00Z", "2025-01-01T00:00:00Z"));
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000082", Task71TestSupport.USER_A, "WON",
                "10.00", "2.0000", "10.00", "20.00",
                "2026-07-03T10:00:00Z", "2026-07-02T10:00:00Z"));
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000083", Task71TestSupport.USER_A, "WON",
                "10.00", "2.0000", "10.00", "20.00",
                "2026-07-01T10:00:00Z", "2026-07-02T10:00:00Z"));

        var response = dashboard(Task71TestSupport.USER_A,
                "?startDate=2026-07-02T07:00:00-03:00&endDate=2026-07-02T10:00:00Z");

        assertThat(response.statusCode()).isEqualTo(200);
        var root = Task71TestSupport.body(response);
        Task71TestSupport.assertCount(root.path("summary"), "betsCount", 1);
        assertThat(Instant.parse(root.path("filters").path("startDate").asText()))
                .isEqualTo(Instant.parse("2026-07-02T10:00:00Z"));
        assertThat(Instant.parse(root.path("filters").path("endDate").asText()))
                .isEqualTo(Instant.parse("2026-07-02T10:00:00Z"));
    }

    @Test
    void should_apply_exact_case_sensitive_text_and_team_matching_without_selection_matching() throws Exception {
        var home = Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000091", Task71TestSupport.USER_A, "WON",
                "10.00", "2.0000", "10.00", "20.00",
                "2026-07-01T10:00:00Z", "2026-07-01T11:00:00Z")
                .withDimensions("FOOTBALL", "Premier League", "Exact Home", "Other", "MATCH_RESULT", "Selection Only");
        var away = Task71TestSupport.row(
                "10000000-0000-0000-0000-000000000092", Task71TestSupport.USER_A, "WON",
                "10.00", "2.0000", "10.00", "20.00",
                "2026-07-01T10:00:00Z", "2026-07-01T11:00:00Z")
                .withDimensions("FOOTBALL", "Premier League", "Other", "Exact Away", "MATCH_RESULT", "Exact Home");
        Task71TestSupport.insertBet(POSTGRES, home);
        Task71TestSupport.insertBet(POSTGRES, away);

        assertBetsCount("?sport=FOOTBALL&league=Premier%20League&market=MATCH_RESULT", 2);
        assertBetsCount("?sport=football", 0);
        assertBetsCount("?sport=FOOTBALL%20", 0);
        assertBetsCount("?league=Premier", 0);
        assertBetsCount("?league=Other%20League", 0);
        assertBetsCount("?market=OTHER_MARKET", 0);
        assertBetsCount("?team=Exact%20Home", 1);
        assertBetsCount("?team=Exact%20Away", 1);
        assertBetsCount("?team=Selection%20Only", 0);
    }

    @Test
    void should_apply_status_and_inclusive_decimal_ranges_with_and_composition_and_echo_normalized_values()
            throws Exception {
        Task71TestSupport.insertMixedFixture(POSTGRES);

        var response = dashboard(Task71TestSupport.USER_A, "?status=WON&sport=FOOTBALL"
                + "&minOdds=2.09995&maxOdds=2.10004&minStake=99.995&maxStake=100.004");

        assertThat(response.statusCode()).isEqualTo(200);
        var root = Task71TestSupport.body(response);
        Task71TestSupport.assertCount(root.path("summary"), "betsCount", 1);
        var filters = root.path("filters");
        Task71TestSupport.assertExactFields(filters, Task71TestSupport.FILTER_FIELDS);
        assertThat(filters.path("status").asText()).isEqualTo("WON");
        assertThat(filters.path("sport").asText()).isEqualTo("FOOTBALL");
        assertThat(filters.path("minOdds").decimalValue()).isEqualByComparingTo("2.1000");
        assertThat(filters.path("minOdds").decimalValue().scale()).isEqualTo(4);
        assertThat(filters.path("maxOdds").decimalValue()).isEqualByComparingTo("2.1000");
        assertThat(filters.path("maxOdds").decimalValue().scale()).isEqualTo(4);
        assertThat(filters.path("minStake").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(filters.path("minStake").decimalValue().scale()).isEqualTo(2);
        assertThat(filters.path("maxStake").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(filters.path("maxStake").decimalValue().scale()).isEqualTo(2);
    }

    @Test
    void should_echo_valid_filters_even_when_the_filtered_set_is_empty() throws Exception {
        var response = dashboard(Task71TestSupport.USER_A,
                "?sport=TENNIS&status=LOST&minOdds=2.12555&minStake=120.126");

        assertThat(response.statusCode()).isEqualTo(200);
        var root = Task71TestSupport.body(response);
        Task71TestSupport.assertCount(root.path("summary"), "betsCount", 0);
        var filters = root.path("filters");
        Task71TestSupport.assertExactFields(filters, Task71TestSupport.FILTER_FIELDS);
        assertThat(filters.path("sport").asText()).isEqualTo("TENNIS");
        assertThat(filters.path("status").asText()).isEqualTo("LOST");
        assertThat(filters.path("minOdds").decimalValue()).isEqualByComparingTo("2.1256");
        assertThat(filters.path("minStake").decimalValue()).isEqualByComparingTo("120.13");
    }

    @ParameterizedTest(name = "invalid filter {0}")
    @ValueSource(strings = {
            "?startDate=not-an-instant",
            "?minOdds=not-a-decimal",
            "?sport=%20%20",
            "?status=UNKNOWN",
            "?startDate=2026-07-02T00:00:00Z&endDate=2026-07-01T00:00:00Z",
            "?minOdds=1.0000",
            "?minOdds=2.5000&maxOdds=2.4000",
            "?minStake=0.00",
            "?minStake=20.00&maxStake=19.99"
    })
    void should_reject_every_documented_invalid_filter_with_a_safe_400(String query) throws Exception {
        Task71TestSupport.assertSafeError(dashboard(Task71TestSupport.USER_A, query), 400);
    }

    @Test
    void should_not_allow_client_controlled_user_id_to_override_authenticated_ownership() throws Exception {
        Task71TestSupport.insertMixedFixture(POSTGRES);

        var response = dashboard(Task71TestSupport.USER_A, "?userId=" + Task71TestSupport.USER_B);

        assertThat(response.statusCode()).isIn(200, 400);
        if (response.statusCode() == 200) {
            var summary = Task71TestSupport.body(response).path("summary");
            Task71TestSupport.assertMetric(summary, "totalStake", "250.00", 2);
            Task71TestSupport.assertCount(summary, "betsCount", 6);
        } else {
            Task71TestSupport.assertSafeError(response, 400);
        }
    }

    @Test
    void should_leave_analytics_bets_and_processed_events_unchanged_after_dashboard_read() throws Exception {
        Task71TestSupport.insertMixedFixture(POSTGRES);
        Task71TestSupport.insertProcessedEvent(
                POSTGRES, UUID.fromString("77777777-7777-7777-7777-777777777777"));
        var beforeRows = Task71TestSupport.projectionState(POSTGRES);
        var beforeEvents = Task71TestSupport.processedEventState(POSTGRES);
        var beforeBetCount = Task71TestSupport.count(POSTGRES, "analytics_bets");
        var beforeEventCount = Task71TestSupport.count(POSTGRES, "processed_events");

        var response = dashboard(Task71TestSupport.USER_A, "?sport=FOOTBALL");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Task71TestSupport.count(POSTGRES, "analytics_bets")).isEqualTo(beforeBetCount);
        assertThat(Task71TestSupport.count(POSTGRES, "processed_events")).isEqualTo(beforeEventCount);
        assertThat(Task71TestSupport.projectionState(POSTGRES)).containsExactlyElementsOf(beforeRows);
        assertThat(Task71TestSupport.processedEventState(POSTGRES)).containsExactlyElementsOf(beforeEvents);
    }

    private static void assertAllFiltersNull(JsonNode filters) {
        Task71TestSupport.assertExactFields(filters, Task71TestSupport.FILTER_FIELDS);
        for (var field : Task71TestSupport.FILTER_FIELDS) {
            assertThat(filters.path(field).isNull()).as(field + " must be null").isTrue();
        }
    }

    private void assertBetsCount(String query, int expected) throws Exception {
        var response = dashboard(Task71TestSupport.USER_A, query);
        assertThat(response.statusCode()).isEqualTo(200);
        Task71TestSupport.assertCount(Task71TestSupport.body(response).path("summary"), "betsCount", expected);
    }

    private void insertDrawdown(String betId, String status, String profit, String settledAt) throws Exception {
        var profitValue = new java.math.BigDecimal(profit);
        var stake = switch (status) {
            case "WON" -> profitValue.toPlainString();
            case "LOST" -> profitValue.abs().toPlainString();
            default -> "100.00";
        };
        var returnAmount = switch (status) {
            case "WON" -> profitValue.add(new java.math.BigDecimal(stake)).toPlainString();
            case "LOST" -> "0.00";
            case "VOID", "CANCELLED" -> stake;
            default -> new java.math.BigDecimal(stake).add(profitValue).toPlainString();
        };
        Task71TestSupport.insertBet(POSTGRES, Task71TestSupport.row(
                betId, Task71TestSupport.USER_A, status, stake, "2.0000", profit, returnAmount,
                "2026-07-01T09:00:00Z", settledAt));
    }

    private static java.net.http.HttpResponse<String> dashboard(UUID userId, String query) throws Exception {
        return Task71TestSupport.dashboard(context, userId.toString(), query);
    }
}
