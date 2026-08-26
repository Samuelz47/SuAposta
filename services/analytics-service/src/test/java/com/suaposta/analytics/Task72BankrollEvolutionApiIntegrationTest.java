package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class Task72BankrollEvolutionApiIntegrationTest {

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
    void should_return_exact_ordered_points_from_projected_profit_for_authenticated_user() throws Exception {
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000302",
                Task72TestSupport.USER_A, "LOST", "-12.50", "2026-08-01T11:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000301",
                Task72TestSupport.USER_A, "WON", "7.25", "2026-08-01T10:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000303",
                Task72TestSupport.USER_A, "CASHOUT", "0.00", "2026-08-01T12:00:00Z",
                "2026-07-02T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000304",
                Task72TestSupport.USER_A, "WON", "10.00", "2026-08-01T13:00:00Z",
                "2026-07-03T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000305",
                Task72TestSupport.USER_A, "VOID", "999.00", "2026-08-01T14:00:00Z",
                "2026-07-04T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000306",
                Task72TestSupport.USER_A, "CANCELLED", "-999.00", "2026-08-01T15:00:00Z",
                "2026-07-05T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000307",
                Task72TestSupport.USER_A, "PENDING", null, "2026-08-01T16:00:00Z",
                null, "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "20000000-0000-0000-0000-000000000301",
                Task72TestSupport.USER_B, "WON", "999.00", "2026-08-01T17:00:00Z",
                "2026-07-01T09:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");

        var response = bankroll(Task72TestSupport.USER_A, "");

        assertThat(response.statusCode()).isEqualTo(200);
        var root = Task72TestSupport.body(response);
        assertThat(root.fieldNames()).toIterable().containsExactly("points");
        var points = root.path("points");
        assertThat(points).hasSize(4);
        assertPoint(points.get(0), "2026-07-01", "7.25", "7.25", "7.25");
        assertPoint(points.get(1), "2026-07-01", "-12.50", "-5.25", "-5.25");
        assertPoint(points.get(2), "2026-07-02", "0.00", "-5.25", "-5.25");
        assertPoint(points.get(3), "2026-07-03", "10.00", "4.75", "4.75");
        assertThat(response.body()).doesNotContain("betId", "userId", "analytics_bets");
    }

    @Test
    void should_apply_inclusive_settled_at_date_filters_before_cumulative_calculation() throws Exception {
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000311",
                Task72TestSupport.USER_A, "WON", "100.00", "2026-08-01T10:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000312",
                Task72TestSupport.USER_A, "WON", "25.00", "2026-01-01T10:00:00Z",
                "2026-07-02T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000313",
                Task72TestSupport.USER_A, "WON", "100.00", "2026-01-01T10:00:00Z",
                "2026-07-03T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");

        var response = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query(
                "startDate", "2026-07-02T07:00:00-03:00",
                "endDate", "2026-07-02T10:00:00Z"));

        assertThat(response.statusCode()).isEqualTo(200);
        var points = Task72TestSupport.body(response).path("points");
        assertThat(points).hasSize(1);
        assertPoint(points.get(0), "2026-07-02", "25.00", "25.00", "25.00");
    }

    @Test
    void should_compose_exact_dimension_filters_without_matching_selection() throws Exception {
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000321",
                Task72TestSupport.USER_A, "WON", "10.00", "2026-01-01T10:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "Premier League", "Exact Home", "Other", "MATCH_RESULT");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000322",
                Task72TestSupport.USER_A, "WON", "20.00", "2026-01-01T10:00:00Z",
                "2026-07-01T11:00:00Z", "FOOTBALL", "Premier League", "Other", "Other", "MATCH_RESULT");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000323",
                Task72TestSupport.USER_A, "WON", "30.00", "2026-01-01T10:00:00Z",
                "2026-07-01T12:00:00Z", "FOOTBALL", "Premier League", "Other", "Other", "MATCH_RESULT",
                "SelectionOnly");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000325",
                Task72TestSupport.USER_A, "WON", "40.00", "2026-01-01T10:00:00Z",
                "2026-07-01T13:00:00Z", "FOOTBALL", "Premier League", "Other", "AwayOnly", "MATCH_RESULT");

        var response = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query(
                "sport", "FOOTBALL", "league", "Premier League", "team", "Exact Home", "market", "MATCH_RESULT"));

        assertThat(response.statusCode()).isEqualTo(200);
        var points = Task72TestSupport.body(response).path("points");
        assertThat(points).hasSize(1);
        assertPoint(points.get(0), "2026-07-01", "10.00", "10.00", "10.00");

        var selectionAsTeam = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query("team", "SelectionOnly"));
        assertThat(selectionAsTeam.statusCode()).isEqualTo(200);
        assertThat(Task72TestSupport.body(selectionAsTeam).path("points")).isEmpty();

        var awayTeam = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query("team", "AwayOnly"));
        assertThat(awayTeam.statusCode()).isEqualTo(200);
        var awayPoints = Task72TestSupport.body(awayTeam).path("points");
        assertThat(awayPoints).hasSize(1);
        assertPoint(awayPoints.get(0), "2026-07-01", "40.00", "40.00", "40.00");
    }

    @Test
    void should_apply_league_and_market_filters_independently() throws Exception {
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000327",
                Task72TestSupport.USER_A, "WON", "10.00", "2026-01-01T10:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "LEAGUE_A", "Home", "Away", "MARKET_A");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000328",
                Task72TestSupport.USER_A, "WON", "20.00", "2026-01-01T10:00:00Z",
                "2026-07-01T11:00:00Z", "FOOTBALL", "LEAGUE_B", "Home", "Away", "MARKET_A");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000329",
                Task72TestSupport.USER_A, "WON", "30.00", "2026-01-01T10:00:00Z",
                "2026-07-01T12:00:00Z", "FOOTBALL", "LEAGUE_A", "Home", "Away", "MARKET_B");

        var league = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query("league", "LEAGUE_A"));
        assertThat(league.statusCode()).isEqualTo(200);
        var leaguePoints = Task72TestSupport.body(league).path("points");
        assertThat(leaguePoints).hasSize(2);
        assertPoint(leaguePoints.get(0), "2026-07-01", "10.00", "10.00", "10.00");
        assertPoint(leaguePoints.get(1), "2026-07-01", "30.00", "40.00", "40.00");

        var market = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query("market", "MARKET_A"));
        assertThat(market.statusCode()).isEqualTo(200);
        var marketPoints = Task72TestSupport.body(market).path("points");
        assertThat(marketPoints).hasSize(2);
        assertPoint(marketPoints.get(0), "2026-07-01", "10.00", "10.00", "10.00");
        assertPoint(marketPoints.get(1), "2026-07-01", "20.00", "30.00", "30.00");

        var missingLeague = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query("league", "NO_LEAGUE"));
        assertThat(missingLeague.statusCode()).isEqualTo(200);
        assertThat(Task72TestSupport.body(missingLeague).path("points")).isEmpty();

        var missingMarket = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query("market", "NO_MARKET"));
        assertThat(missingMarket.statusCode()).isEqualTo(200);
        assertThat(Task72TestSupport.body(missingMarket).path("points")).isEmpty();
    }

    @ParameterizedTest(name = "{0} exact text matching")
    @MethodSource("documentedTextFilterCases")
    void should_apply_documented_exact_case_sensitive_text_matching_and_reject_blank_text(
            String parameter, String exact, String lowercase, String trailingSpace) throws Exception {
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000324",
                Task72TestSupport.USER_A, "WON", "10.00", "2026-01-01T10:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "Premier League", "Home", "Away", "MATCH_RESULT");

        var exactResponse = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query(parameter, exact));
        assertThat(exactResponse.statusCode()).isEqualTo(200);
        assertThat(Task72TestSupport.body(exactResponse).path("points")).hasSize(1);

        var lowercaseResponse = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query(parameter, lowercase));
        assertThat(lowercaseResponse.statusCode()).isEqualTo(200);
        assertThat(Task72TestSupport.body(lowercaseResponse).path("points")).isEmpty();

        var trailingSpaceResponse = bankroll(
                Task72TestSupport.USER_A, Task72TestSupport.query(parameter, trailingSpace));
        assertThat(trailingSpaceResponse.statusCode()).isEqualTo(200);
        assertThat(Task72TestSupport.body(trailingSpaceResponse).path("points")).isEmpty();

        Task72TestSupport.assertSafeError(
                bankroll(Task72TestSupport.USER_A, Task72TestSupport.query(parameter, "")), 400);
    }

    private static Stream<Arguments> documentedTextFilterCases() {
        return Stream.of(
                Arguments.of("sport", "FOOTBALL", "football", "FOOTBALL "),
                Arguments.of("league", "Premier League", "premier league", "Premier League "),
                Arguments.of("market", "MATCH_RESULT", "match_result", "MATCH_RESULT "));
    }

    @Test
    void should_never_let_a_client_user_id_query_parameter_override_authenticated_ownership() throws Exception {
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000326",
                Task72TestSupport.USER_A, "WON", "10.00", "2026-01-01T10:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "20000000-0000-0000-0000-000000000326",
                Task72TestSupport.USER_B, "WON", "100000.00", "2026-01-01T10:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");

        var response = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query(
                "userId", Task72TestSupport.USER_B.toString()));

        assertThat(response.statusCode()).isIn(200, 400);
        if (response.statusCode() == 400) {
            Task72TestSupport.assertSafeError(response, 400);
            return;
        }
        var points = Task72TestSupport.body(response).path("points");
        assertThat(points).hasSize(1);
        assertPoint(points.get(0), "2026-07-01", "10.00", "10.00", "10.00");
    }

    @Test
    void should_return_successful_empty_points_for_only_ineligible_or_nonmatching_data() throws Exception {
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000331",
                Task72TestSupport.USER_A, "PENDING", null, "2026-01-01T10:00:00Z",
                null, "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000332",
                Task72TestSupport.USER_A, "VOID", "0.00", "2026-01-01T10:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");

        var response = bankroll(Task72TestSupport.USER_A, Task72TestSupport.query("sport", "TENNIS"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Task72TestSupport.body(response).path("points")).isEmpty();
    }

    @Test
    void should_reject_missing_identity_malformed_dates_and_reversed_date_ranges() throws Exception {
        Task72TestSupport.assertSafeError(bankroll((String) null, ""), 401);
        Task72TestSupport.assertSafeError(bankroll("not-a-uuid", ""), 401);
        Task72TestSupport.assertSafeError(bankroll(Task72TestSupport.USER_A,
                Task72TestSupport.query("startDate", "not-an-instant")), 400);
        Task72TestSupport.assertSafeError(bankroll(Task72TestSupport.USER_A, Task72TestSupport.query(
                "startDate", "2026-07-03T00:00:00Z", "endDate", "2026-07-02T00:00:00Z")), 400);
    }

    @Test
    void should_leave_projection_and_processed_event_tables_unchanged_after_read() throws Exception {
        Task72TestSupport.insert(POSTGRES, "10000000-0000-0000-0000-000000000341",
                Task72TestSupport.USER_A, "WON", "10.00", "2026-01-01T10:00:00Z",
                "2026-07-01T10:00:00Z", "FOOTBALL", "League", "Home", "Away", "MARKET");
        Task71TestSupport.insertProcessedEvent(
                POSTGRES, UUID.fromString("77777777-7777-7777-7777-777777777777"));
        var beforeRows = Task71TestSupport.projectionState(POSTGRES);
        var beforeEvents = Task71TestSupport.processedEventState(POSTGRES);
        var beforeBetCount = Task71TestSupport.count(POSTGRES, "analytics_bets");
        var beforeEventCount = Task71TestSupport.count(POSTGRES, "processed_events");

        var response = bankroll(Task72TestSupport.USER_A, "");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Task71TestSupport.count(POSTGRES, "analytics_bets")).isEqualTo(beforeBetCount);
        assertThat(Task71TestSupport.count(POSTGRES, "processed_events")).isEqualTo(beforeEventCount);
        assertThat(Task71TestSupport.projectionState(POSTGRES)).containsExactlyElementsOf(beforeRows);
        assertThat(Task71TestSupport.processedEventState(POSTGRES)).containsExactlyElementsOf(beforeEvents);
    }

    private static java.net.http.HttpResponse<String> bankroll(UUID userId, String query) throws Exception {
        return bankroll(userId == null ? null : userId.toString(), query);
    }

    private static java.net.http.HttpResponse<String> bankroll(String userId, String query) throws Exception {
        return Task72TestSupport.bankrollEvolution(context, userId, query);
    }

    private static void assertPoint(
            JsonNode point, String date, String profit, String cumulativeProfit, String bankroll) {
        Task72TestSupport.assertExactPointFields(point);
        assertThat(point.path("date").asText()).isEqualTo(date);
        Task72TestSupport.assertMoney(point, "profit", profit);
        Task72TestSupport.assertMoney(point, "cumulativeProfit", cumulativeProfit);
        Task72TestSupport.assertMoney(point, "bankroll", bankroll);
    }
}
