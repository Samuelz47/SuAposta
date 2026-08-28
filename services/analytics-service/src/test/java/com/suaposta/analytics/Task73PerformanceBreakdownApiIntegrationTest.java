package com.suaposta.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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
class Task73PerformanceBreakdownApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16.4-alpine"));

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startApplication() {
        context = Task73TestSupport.startApplication(POSTGRES);
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        Task73TestSupport.resetDatabase(POSTGRES);
    }

    @Test
    void should_return_exact_metrics_per_league_without_cross_user_data() throws Exception {
        insertLeagueFixture();

        var response = breakdown(Task73TestSupport.USER_A,
                Task73TestSupport.query("groupBy", "LEAGUE"));

        assertThat(response.statusCode()).isEqualTo(200);
        var root = Task73TestSupport.body(response);
        Task73TestSupport.assertExactFields(root, Set.of("groupBy", "items"));
        assertThat(root.path("groupBy").asText()).isEqualTo("LEAGUE");
        var items = root.path("items");
        assertThat(items).hasSize(2);
        assertItemFields(items.get(0));
        assertItemFields(items.get(1));
        assertThat(items.get(0).path("name").asText()).isEqualTo("LEAGUE_A");
        assertThat(items.get(1).path("name").asText()).isEqualTo("LEAGUE_B");

        var leagueA = items.get(0);
        Task73TestSupport.assertMetric(leagueA, "totalStake", "250.00", 2);
        Task73TestSupport.assertMetric(leagueA, "profit", "40.00", 2);
        Task73TestSupport.assertMetric(leagueA, "roi", "16.00", 2);
        Task73TestSupport.assertMetric(leagueA, "yield", "16.00", 2);
        Task73TestSupport.assertMetric(leagueA, "winRate", "50.00", 2);
        Task73TestSupport.assertMetric(leagueA, "avgOdds", "1.9667", 4);
        Task73TestSupport.assertMetric(leagueA, "drawdown", "70.00", 2);
        Task73TestSupport.assertCount(leagueA, "betsCount", 6);
        Task73TestSupport.assertCount(leagueA, "pendingCount", 1);
        Task73TestSupport.assertCount(leagueA, "wonCount", 1);
        Task73TestSupport.assertCount(leagueA, "lostCount", 1);
        Task73TestSupport.assertCount(leagueA, "voidCount", 1);
        Task73TestSupport.assertCount(leagueA, "cashoutCount", 1);
        Task73TestSupport.assertCount(leagueA, "cancelledCount", 1);

        var leagueB = items.get(1);
        Task73TestSupport.assertMetric(leagueB, "totalStake", "800.00", 2);
        Task73TestSupport.assertMetric(leagueB, "profit", "100.00", 2);
        Task73TestSupport.assertMetric(leagueB, "roi", "12.50", 2);
        Task73TestSupport.assertMetric(leagueB, "yield", "12.50", 2);
        Task73TestSupport.assertMetric(leagueB, "winRate", "50.00", 2);
        Task73TestSupport.assertMetric(leagueB, "avgOdds", "2.0000", 4);
        Task73TestSupport.assertMetric(leagueB, "drawdown", "100.00", 2);
        Task73TestSupport.assertCount(leagueB, "betsCount", 2);
        Task73TestSupport.assertCount(leagueB, "pendingCount", 0);
        Task73TestSupport.assertCount(leagueB, "wonCount", 1);
        Task73TestSupport.assertCount(leagueB, "lostCount", 1);
    }

    @ParameterizedTest(name = "exact {0} filter")
    @MethodSource("exactTextFilters")
    void should_apply_exact_sport_league_and_market_filters(
            String filter, String value, String groupBy, String expectedName, int expectedBets) throws Exception {
        insertGroupingFixture();

        var response = breakdown(Task73TestSupport.USER_A,
                Task73TestSupport.query("groupBy", groupBy, filter, value));

        assertThat(response.statusCode()).isEqualTo(200);
        var items = Task73TestSupport.body(response).path("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("name").asText()).isEqualTo(expectedName);
        Task73TestSupport.assertCount(items.get(0), "betsCount", expectedBets);
    }

    static Stream<Arguments> exactTextFilters() {
        return Stream.of(
                Arguments.of("sport", "FOOTBALL", "SPORT", "FOOTBALL", 2),
                Arguments.of("league", "LEAGUE_A", "LEAGUE", "LEAGUE_A", 2),
                Arguments.of("market", "MARKET_A", "MARKET", "MARKET_A", 2));
    }

    @ParameterizedTest(name = "groupBy={0}")
    @MethodSource("supportedGroupings")
    void should_use_the_documented_group_key_for_every_supported_grouping(
            String groupBy, List<String> expectedNames) throws Exception {
        insertGroupingFixture();

        var response = breakdown(Task73TestSupport.USER_A,
                Task73TestSupport.query("groupBy", groupBy));

        assertThat(response.statusCode()).isEqualTo(200);
        var items = Task73TestSupport.body(response).path("items");
        assertThat(items).hasSize(expectedNames.size());
        assertThat(Stream.iterate(0, index -> index + 1)
                .limit(items.size())
                .map(index -> items.get(index).path("name").asText())
                .toList()).containsExactlyElementsOf(expectedNames);
    }

    static Stream<Arguments> supportedGroupings() {
        return Stream.of(
                Arguments.of("SPORT", List.of("FOOTBALL", "TENNIS")),
                Arguments.of("LEAGUE", List.of("LEAGUE_A", "LEAGUE_B")),
                Arguments.of("TEAM", List.of("AWAY_A", "AWAY_B", "AWAY_C", "HOME_A", "HOME_B")),
                Arguments.of("MARKET", List.of("MARKET_A", "MARKET_B")),
                Arguments.of("MONTH", List.of("2026-01", "2026-02")),
                Arguments.of("WEEK", List.of("2026-W01", "2026-W03", "2026-W06")),
                Arguments.of("DAY", List.of("2026-01-01", "2026-01-15", "2026-02-03")));
    }

    @Test
    void should_apply_all_filters_before_grouping_and_calculation() throws Exception {
        insert("30000000-0000-0000-0000-000000000001", Task73TestSupport.USER_A, "WON",
                "100.00", "2.0000", "100.00", "200.00", "2026-07-01T10:00:00Z",
                "2026-07-10T10:00:00Z", "FOOTBALL", "LEAGUE_A", "HOME", "AWAY", "MARKET_A", "HOME");
        insert("30000000-0000-0000-0000-000000000002", Task73TestSupport.USER_A, "WON",
                "10.00", "2.0000", "10.00", "20.00", "2026-07-02T10:00:00Z",
                "2026-07-10T11:00:00Z", "FOOTBALL", "LEAGUE_A", "HOME", "AWAY", "MARKET_A", "HOME");
        insert("30000000-0000-0000-0000-000000000003", Task73TestSupport.USER_A, "WON",
                "999.00", "2.0000", "999.00", "1998.00", "2026-07-02T10:00:00Z",
                "2026-07-10T12:00:00Z", "TENNIS", "LEAGUE_A", "HOME", "AWAY", "MARKET_A", "HOME");
        insert("30000000-0000-0000-0000-000000000004", Task73TestSupport.USER_A, "WON",
                "50.00", "2.0000", "50.00", "100.00", "2026-07-02T10:00:00Z",
                "2026-07-10T13:00:00Z", "FOOTBALL", "LEAGUE_B", "HOME", "AWAY", "MARKET_A", "HOME");

        var response = breakdown(Task73TestSupport.USER_A, Task73TestSupport.query(
                "groupBy", "LEAGUE", "sport", "FOOTBALL",
                "startDate", "2026-07-02T07:00:00-03:00", "endDate", "2026-07-02T10:00:00Z",
                "market", "MARKET_A"));

        assertThat(response.statusCode()).isEqualTo(200);
        var items = Task73TestSupport.body(response).path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("name").asText()).isEqualTo("LEAGUE_A");
        Task73TestSupport.assertMetric(items.get(0), "totalStake", "10.00", 2);
        Task73TestSupport.assertMetric(items.get(0), "profit", "10.00", 2);
        assertThat(items.get(1).path("name").asText()).isEqualTo("LEAGUE_B");
        Task73TestSupport.assertMetric(items.get(1), "totalStake", "50.00", 2);
        Task73TestSupport.assertMetric(items.get(1), "profit", "50.00", 2);
    }

    @ParameterizedTest(name = "non-exact {0}={1}")
    @MethodSource("nonExactTextFilters")
    void should_keep_sport_league_and_market_filters_case_sensitive_and_untrimmed(
            String filter, String value, String groupBy, int expectedStatus) throws Exception {
        insertGroupingFixture();

        var response = breakdown(Task73TestSupport.USER_A,
                Task73TestSupport.query("groupBy", groupBy, filter, value));

        if (expectedStatus == 400) {
            Task73TestSupport.assertSafeError(response, 400);
            return;
        }
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Task73TestSupport.body(response).path("items")).isEmpty();
    }

    static Stream<Arguments> nonExactTextFilters() {
        return Stream.of(
                Arguments.of("sport", "football", "SPORT", 200),
                Arguments.of("sport", "FOOTBALL ", "SPORT", 200),
                Arguments.of("sport", "", "SPORT", 400),
                Arguments.of("league", "league_a", "LEAGUE", 200),
                Arguments.of("league", "LEAGUE_A ", "LEAGUE", 200),
                Arguments.of("league", "", "LEAGUE", 400),
                Arguments.of("market", "market_a", "MARKET", 200),
                Arguments.of("market", "MARKET_A ", "MARKET", 200),
                Arguments.of("market", "", "MARKET", 400));
    }

    @Test
    void should_keep_an_observed_bucket_when_all_rows_are_ineligible_for_performance_metrics() throws Exception {
        insertIneligibleFixture();

        var response = breakdown(Task73TestSupport.USER_A,
                Task73TestSupport.query("groupBy", "LEAGUE"));

        assertThat(response.statusCode()).isEqualTo(200);
        var items = Task73TestSupport.body(response).path("items");
        assertThat(items).hasSize(1);
        var item = items.get(0);
        assertItemFields(item);
        assertThat(item.path("name").asText()).isEqualTo("INELIGIBLE_ONLY");
        Task73TestSupport.assertMetric(item, "totalStake", "0.00", 2);
        Task73TestSupport.assertMetric(item, "profit", "0.00", 2);
        Task73TestSupport.assertMetric(item, "roi", "0.00", 2);
        Task73TestSupport.assertMetric(item, "yield", "0.00", 2);
        Task73TestSupport.assertMetric(item, "winRate", "0.00", 2);
        Task73TestSupport.assertMetric(item, "avgOdds", "0.0000", 4);
        Task73TestSupport.assertMetric(item, "drawdown", "0.00", 2);
        Task73TestSupport.assertCount(item, "betsCount", 3);
        Task73TestSupport.assertCount(item, "pendingCount", 1);
        Task73TestSupport.assertCount(item, "wonCount", 0);
        Task73TestSupport.assertCount(item, "lostCount", 0);
        Task73TestSupport.assertCount(item, "voidCount", 1);
        Task73TestSupport.assertCount(item, "cashoutCount", 0);
        Task73TestSupport.assertCount(item, "cancelledCount", 1);
    }

    @Test
    void should_assign_team_metrics_to_home_and_away_without_using_selection_or_double_counting_same_team()
            throws Exception {
        insert("40000000-0000-0000-0000-000000000001", Task73TestSupport.USER_A, "WON",
                "100.00", "2.0000", "100.00", "200.00", "2026-07-01T10:00:00Z",
                "2026-07-10T10:00:00Z", "FOOTBALL", "LEAGUE", "TEAM_A", "TEAM_B", "MARKET", "SELECTION_ONLY");
        insert("40000000-0000-0000-0000-000000000002", Task73TestSupport.USER_A, "LOST",
                "100.00", "2.0000", "-100.00", "0.00", "2026-07-02T10:00:00Z",
                "2026-07-10T11:00:00Z", "FOOTBALL", "LEAGUE", "TEAM_B", "TEAM_B", "MARKET", "SELECTION_ONLY");

        var response = breakdown(Task73TestSupport.USER_A, Task73TestSupport.query("groupBy", "TEAM"));

        assertThat(response.statusCode()).isEqualTo(200);
        var items = Task73TestSupport.body(response).path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("name").asText()).isEqualTo("TEAM_A");
        Task73TestSupport.assertCount(items.get(0), "betsCount", 1);
        Task73TestSupport.assertMetric(items.get(0), "profit", "100.00", 2);
        assertThat(items.get(1).path("name").asText()).isEqualTo("TEAM_B");
        Task73TestSupport.assertCount(items.get(1), "betsCount", 2);
        Task73TestSupport.assertMetric(items.get(1), "profit", "0.00", 2);
    }

    @Test
    void should_use_the_iso_week_based_year_for_week_buckets() throws Exception {
        insert("50000000-0000-0000-0000-000000000001", Task73TestSupport.USER_A, "WON",
                "10.00", "2.0000", "10.00", "20.00", "2021-01-01T12:00:00Z",
                "2021-01-01T13:00:00Z", "FOOTBALL", "ISO_WEEK", "HOME", "AWAY", "MARKET", "HOME");

        var response = breakdown(Task73TestSupport.USER_A,
                Task73TestSupport.query("groupBy", "WEEK"));

        assertThat(response.statusCode()).isEqualTo(200);
        var items = Task73TestSupport.body(response).path("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("name").asText()).isEqualTo("2020-W53");
    }

    @Test
    void should_group_day_and_month_in_utc_not_in_the_system_default_zone() throws Exception {
        var previousZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            insert("50000000-0000-0000-0000-000000000002", Task73TestSupport.USER_A, "WON",
                    "10.00", "2.0000", "10.00", "20.00", "2026-01-01T00:30:00Z",
                    "2026-01-01T01:30:00Z", "FOOTBALL", "UTC_BOUNDARY", "HOME", "AWAY", "MARKET", "HOME");

            var dayResponse = breakdown(Task73TestSupport.USER_A,
                    Task73TestSupport.query("groupBy", "DAY"));
            assertThat(dayResponse.statusCode()).isEqualTo(200);
            assertThat(Task73TestSupport.body(dayResponse).path("items").get(0).path("name").asText())
                    .isEqualTo("2026-01-01");

            var monthResponse = breakdown(Task73TestSupport.USER_A,
                    Task73TestSupport.query("groupBy", "MONTH"));
            assertThat(monthResponse.statusCode()).isEqualTo(200);
            assertThat(Task73TestSupport.body(monthResponse).path("items").get(0).path("name").asText())
                    .isEqualTo("2026-01");
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    @ParameterizedTest
    @MethodSource("invalidGroupingQueries")
    void should_reject_missing_blank_case_variant_and_unknown_grouping(String query) throws Exception {
        Task73TestSupport.assertSafeError(
                breakdown(Task73TestSupport.USER_A, query), 400);
    }

    static Stream<String> invalidGroupingQueries() {
        return Stream.of("", "?groupBy=", "?groupBy=%20", "?groupBy=sport", "?groupBy=Sport",
                "?groupBy=SPORT%20", "?groupBy=UNKNOWN");
    }

    @Test
    void should_validate_identity_before_grouping_and_filter_validation() throws Exception {
        Task73TestSupport.assertSafeError(
                breakdown((String) null, Task73TestSupport.query("groupBy", "UNKNOWN", "sport", "")), 401);
        Task73TestSupport.assertSafeError(
                breakdown("not-a-uuid", Task73TestSupport.query("groupBy", "UNKNOWN")), 401);
    }

    @Test
    void should_reject_non_canonical_uuid_identity() throws Exception {
        Task73TestSupport.assertSafeError(
                breakdown("1-1-1-1-1", Task73TestSupport.query("groupBy", "SPORT")), 401);
    }

    @Test
    void should_reject_malformed_reversed_and_blank_filters_but_preserve_exact_matching() throws Exception {
        insertGroupingFixture();

        Task73TestSupport.assertSafeError(
                breakdown(Task73TestSupport.USER_A, Task73TestSupport.query(
                        "groupBy", "LEAGUE", "startDate", "not-an-instant")), 400);
        Task73TestSupport.assertSafeError(
                breakdown(Task73TestSupport.USER_A, Task73TestSupport.query(
                        "groupBy", "LEAGUE", "startDate", "2026-07-03T00:00:00Z",
                        "endDate", "2026-07-02T00:00:00Z")), 400);
    }

    @Test
    void should_return_an_empty_observed_group_collection_when_filters_match_nothing() throws Exception {
        var response = breakdown(Task73TestSupport.USER_A,
                Task73TestSupport.query("groupBy", "LEAGUE", "sport", "TENNIS"));

        assertThat(response.statusCode()).isEqualTo(200);
        var root = Task73TestSupport.body(response);
        Task73TestSupport.assertExactFields(root, Set.of("groupBy", "items"));
        assertThat(root.path("groupBy").asText()).isEqualTo("LEAGUE");
        assertThat(root.path("items")).isEmpty();
    }

    @ParameterizedTest(name = "blank {0} group key")
    @MethodSource("blankGroupingDimensions")
    void should_omit_null_and_blank_grouping_keys(String groupBy, String expectedName) throws Exception {
        insertBlankGroupingFixture(groupBy);

        var response = breakdown(Task73TestSupport.USER_A,
                Task73TestSupport.query("groupBy", groupBy));

        assertThat(response.statusCode()).isEqualTo(200);
        var names = StreamSupport.stream(Task73TestSupport.body(response).path("items").spliterator(), false)
                .map(item -> item.path("name").isNull() ? null : item.path("name").asText())
                .toList();
        assertThat(names).containsExactly(expectedName);
        assertThat(names).doesNotContain("", " ", "UNKNOWN", null);
    }

    static Stream<Arguments> blankGroupingDimensions() {
        return Stream.of(
                Arguments.of("SPORT", "FOOTBALL"),
                Arguments.of("LEAGUE", "VALID_LEAGUE"),
                Arguments.of("MARKET", "VALID_MARKET"));
    }

    @Test
    void should_never_use_a_client_user_id_query_parameter_as_ownership() throws Exception {
        insertLeagueFixture();

        var response = breakdown(Task73TestSupport.USER_A, Task73TestSupport.query(
                "groupBy", "LEAGUE", "userId", Task73TestSupport.USER_B.toString()));

        assertThat(response.statusCode()).isIn(200, 400);
        if (response.statusCode() == 400) {
            Task73TestSupport.assertSafeError(response, 400);
            return;
        }

        var items = Task73TestSupport.body(response).path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("name").asText()).isEqualTo("LEAGUE_A");
        assertThat(items.get(1).path("name").asText()).isEqualTo("LEAGUE_B");
        Task73TestSupport.assertCount(items.get(0), "betsCount", 6);
        Task73TestSupport.assertCount(items.get(1), "betsCount", 2);
    }

    @Test
    void should_leave_projection_and_processed_event_tables_unchanged_after_read() throws Exception {
        insertLeagueFixture();
        Task71TestSupport.insertProcessedEvent(
                POSTGRES, UUID.fromString("77777777-7777-7777-7777-777777777777"));
        var beforeRows = Task71TestSupport.projectionState(POSTGRES);
        var beforeEvents = Task71TestSupport.processedEventState(POSTGRES);

        var response = breakdown(Task73TestSupport.USER_A,
                Task73TestSupport.query("groupBy", "LEAGUE", "sport", "FOOTBALL"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Task71TestSupport.projectionState(POSTGRES)).containsExactlyElementsOf(beforeRows);
        assertThat(Task71TestSupport.processedEventState(POSTGRES)).containsExactlyElementsOf(beforeEvents);
    }

    private static void assertItemFields(JsonNode item) {
        Task73TestSupport.assertExactFields(item, Task73TestSupport.ITEM_FIELDS);
    }

    private static void insertLeagueFixture() throws Exception {
        insert("10000000-0000-0000-0000-000000000001", Task73TestSupport.USER_A, "WON",
                "100.00", "2.1000", "110.00", "210.00", "2026-07-01T10:00:00Z",
                "2026-07-10T10:00:00Z", "FOOTBALL", "LEAGUE_A", "HOME_A", "AWAY_A", "MARKET_A", "HOME_A");
        insert("10000000-0000-0000-0000-000000000002", Task73TestSupport.USER_A, "LOST",
                "50.00", "2.0000", "-50.00", "0.00", "2026-07-02T10:00:00Z",
                "2026-07-10T11:00:00Z", "FOOTBALL", "LEAGUE_A", "HOME_A", "AWAY_A", "MARKET_A", "HOME_A");
        insert("10000000-0000-0000-0000-000000000003", Task73TestSupport.USER_A, "CASHOUT",
                "100.00", "1.8000", "-20.00", "80.00", "2026-07-03T10:00:00Z",
                "2026-07-10T12:00:00Z", "FOOTBALL", "LEAGUE_A", "HOME_A", "AWAY_A", "MARKET_A", "HOME_A");
        insert("10000000-0000-0000-0000-000000000004", Task73TestSupport.USER_A, "VOID",
                "70.00", "3.5000", "0.00", "70.00", "2026-07-04T10:00:00Z",
                "2026-07-10T13:00:00Z", "FOOTBALL", "LEAGUE_A", "HOME_A", "AWAY_A", "MARKET_A", "HOME_A");
        insert("10000000-0000-0000-0000-000000000005", Task73TestSupport.USER_A, "CANCELLED",
                "80.00", "4.5000", "0.00", "80.00", "2026-07-05T10:00:00Z",
                "2026-07-10T14:00:00Z", "FOOTBALL", "LEAGUE_A", "HOME_A", "AWAY_A", "MARKET_A", "HOME_A");
        insert("10000000-0000-0000-0000-000000000006", Task73TestSupport.USER_A, "PENDING",
                "90.00", "5.5000", null, null, "2026-07-06T10:00:00Z", null,
                "FOOTBALL", "LEAGUE_A", "HOME_A", "AWAY_A", "MARKET_A", "HOME_A");
        insert("10000000-0000-0000-0000-000000000007", Task73TestSupport.USER_A, "WON",
                "400.00", "2.5000", "200.00", "600.00", "2026-07-07T10:00:00Z",
                "2026-07-11T10:00:00Z", "FOOTBALL", "LEAGUE_B", "HOME_B", "AWAY_B", "MARKET_B", "HOME_B");
        insert("10000000-0000-0000-0000-000000000008", Task73TestSupport.USER_A, "LOST",
                "400.00", "1.5000", "-100.00", "0.00", "2026-07-08T10:00:00Z",
                "2026-07-11T11:00:00Z", "FOOTBALL", "LEAGUE_B", "HOME_B", "AWAY_B", "MARKET_B", "HOME_B");
        insert("90000000-0000-0000-0000-000000000001", Task73TestSupport.USER_B, "WON",
                "999999.99", "9.9999", "99999.99", "999999.99", "2026-07-01T10:00:00Z",
                "2026-07-10T09:00:00Z", "FOOTBALL", "AAA", "OTHER_HOME", "OTHER_AWAY", "MARKET_X", "OTHER_HOME");
    }

    private static void insertGroupingFixture() throws Exception {
        insert("20000000-0000-0000-0000-000000000001", Task73TestSupport.USER_A, "WON",
                "100.00", "2.0000", "100.00", "200.00", "2026-01-01T10:00:00Z",
                "2026-02-01T10:00:00Z", "FOOTBALL", "LEAGUE_A", "HOME_A", "AWAY_A", "MARKET_A", "SELECTION_A");
        insert("20000000-0000-0000-0000-000000000002", Task73TestSupport.USER_A, "LOST",
                "100.00", "2.0000", "-100.00", "0.00", "2026-01-15T10:00:00Z",
                "2026-02-02T10:00:00Z", "TENNIS", "LEAGUE_B", "HOME_B", "AWAY_B", "MARKET_B", "SELECTION_B");
        insert("20000000-0000-0000-0000-000000000003", Task73TestSupport.USER_A, "CASHOUT",
                "100.00", "2.0000", "10.00", "110.00", "2026-02-03T10:00:00Z",
                "2026-02-03T12:00:00Z", "FOOTBALL", "LEAGUE_A", "HOME_A", "AWAY_C", "MARKET_A", "SELECTION_C");
        insert("20000000-0000-0000-0000-000000000004", Task73TestSupport.USER_A, "PENDING",
                "100.00", "2.0000", null, null, "2026-02-03T11:00:00Z", null,
                "TENNIS", "LEAGUE_B", "HOME_B", "AWAY_B", "MARKET_B", "SELECTION_B");
    }

    private static void insertIneligibleFixture() throws Exception {
        insert("60000000-0000-0000-0000-000000000001", Task73TestSupport.USER_A, "PENDING",
                "100.00", "2.0000", null, null, "2026-07-01T10:00:00Z", null,
                "FOOTBALL", "INELIGIBLE_ONLY", "HOME", "AWAY", "MARKET", "HOME");
        insert("60000000-0000-0000-0000-000000000002", Task73TestSupport.USER_A, "VOID",
                "70.00", "3.5000", "0.00", "70.00", "2026-07-02T10:00:00Z",
                "2026-07-02T11:00:00Z", "FOOTBALL", "INELIGIBLE_ONLY", "HOME", "AWAY", "MARKET", "HOME");
        insert("60000000-0000-0000-0000-000000000003", Task73TestSupport.USER_A, "CANCELLED",
                "80.00", "4.5000", "0.00", "80.00", "2026-07-03T10:00:00Z",
                "2026-07-03T11:00:00Z", "FOOTBALL", "INELIGIBLE_ONLY", "HOME", "AWAY", "MARKET", "HOME");
    }

    private static void insertBlankGroupingFixture(String groupBy) throws Exception {
        insert("70000000-0000-0000-0000-000000000001", Task73TestSupport.USER_A, "WON",
                "10.00", "2.0000", "10.00", "20.00", "2026-07-01T10:00:00Z",
                "2026-07-01T11:00:00Z", "FOOTBALL", "VALID_LEAGUE", "HOME", "AWAY", "VALID_MARKET", "HOME");
        insert("70000000-0000-0000-0000-000000000002", Task73TestSupport.USER_A, "WON",
                "20.00", "2.0000", "20.00", "40.00", "2026-07-02T10:00:00Z",
                "2026-07-02T11:00:00Z", "SPORT".equals(groupBy) ? " " : "FOOTBALL",
                "LEAGUE".equals(groupBy) ? " " : "VALID_LEAGUE", "HOME", "AWAY",
                "MARKET".equals(groupBy) ? " " : "VALID_MARKET", "HOME");
    }

    private static void insert(
            String betId, UUID userId, String status, String stake, String odds, String profit,
            String returnAmount, String placedAt, String settledAt, String sport, String league,
            String home, String away, String market, String selection) throws Exception {
        Task73TestSupport.insert(POSTGRES, Task71TestSupport.row(
                betId, userId, status, stake, odds, profit, returnAmount, placedAt, settledAt)
                .withDimensions(sport, league, home, away, market, selection));
    }

    private static HttpResponse<String> breakdown(UUID userId, String query) throws Exception {
        return breakdown(userId == null ? null : userId.toString(), query);
    }

    private static HttpResponse<String> breakdown(String userId, String query) throws Exception {
        return Task73TestSupport.breakdown(context, userId, query);
    }
}
