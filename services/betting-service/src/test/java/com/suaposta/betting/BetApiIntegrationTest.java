package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;

class BetApiIntegrationTest {

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startApplication() {
        context = BetTestSupport.startApplication();
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void should_return_created_with_exact_pending_contract_when_creation_is_valid() throws Exception {
        var userId = UUID.randomUUID();
        var request = BetTestSupport.validCreateRequest();

        var response = BetTestSupport.createBet(context, userId, request);

        BetTestSupport.assertCreatedBetResponse(response, userId, request, "100.00", "2.1000");
        var betId = BetTestSupport.responseBetId(response);

        var retrieved = BetTestSupport.getBet(context, userId, betId.toString());

        BetTestSupport.assertRetrievedBetResponse(
                retrieved, userId, request, "100.00", "2.1000");
    }

    @Test
    void should_normalize_stake_and_odds_using_the_task_5_1_precision_contract() throws Exception {
        var userId = UUID.randomUUID();
        var request = BetTestSupport.validCreateRequest();
        request.put("stake", new BigDecimal("10.126"));
        request.put("odds", new BigDecimal("2.12555"));

        var response = BetTestSupport.createBet(context, userId, request);

        BetTestSupport.assertCreatedBetResponse(response, userId, request, "10.13", "2.1256");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIdentityRequests")
    void should_return_401_when_authenticated_identity_is_missing_or_malformed(
            String scenario,
            String endpoint,
            String userIdHeader) throws Exception {
        HttpResponse<String> response;
        String expectedPath;
        if (endpoint.equals("create")) {
            response = BetTestSupport.createBet(
                    context, userIdHeader, BetTestSupport.validCreateRequest());
            expectedPath = BetTestSupport.BETS_PATH;
        } else if (endpoint.equals("list")) {
            response = BetTestSupport.listBets(context, userIdHeader, Map.of("page", "0", "size", "20"));
            expectedPath = BetTestSupport.BETS_PATH;
        } else {
            var betId = UUID.randomUUID();
            response = BetTestSupport.getBet(context, userIdHeader, betId.toString());
            expectedPath = BetTestSupport.BETS_PATH + "/" + betId;
        }

        BetTestSupport.assertErrorEnvelope(response, 401, expectedPath);
    }

    private static Stream<Arguments> invalidIdentityRequests() {
        return Stream.of(
                Arguments.of("create without X-User-Id", "create", null),
                Arguments.of("create with malformed X-User-Id", "create", "not-a-user-uuid"),
                Arguments.of("list without X-User-Id", "list", null),
                Arguments.of("list with malformed X-User-Id", "list", "not-a-user-uuid"),
                Arguments.of("get without X-User-Id", "get", null),
                Arguments.of("get with malformed X-User-Id", "get", "not-a-user-uuid"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStakes")
    void should_reject_invalid_stake_through_the_api_without_persisting_a_bet(
            String scenario,
            BigDecimal stake) throws Exception {
        var userId = UUID.randomUUID();
        var request = BetTestSupport.validCreateRequest();
        request.put("stake", stake);

        var response = BetTestSupport.createBet(context, userId, request);

        BetTestSupport.assertErrorEnvelope(response, 400, BetTestSupport.BETS_PATH);
        var list = BetTestSupport.listBets(context, userId, Map.of("page", "0", "size", "20"));
        BetTestSupport.assertListEnvelope(list);
        assertThat(BetTestSupport.json(list).get("content").size()).isZero();
        assertThat(BetTestSupport.json(list).get("totalElements").asLong()).isZero();
    }

    private static Stream<Arguments> invalidStakes() {
        return Stream.of(
                Arguments.of("zero", new BigDecimal("0")),
                Arguments.of("negative", new BigDecimal("-0.01")),
                Arguments.of("positive value rounded to zero", new BigDecimal("0.004")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOdds")
    void should_reject_invalid_odds_through_the_api_without_persisting_a_bet(
            String scenario,
            BigDecimal odds) throws Exception {
        var userId = UUID.randomUUID();
        var request = BetTestSupport.validCreateRequest();
        request.put("odds", odds);

        var response = BetTestSupport.createBet(context, userId, request);

        BetTestSupport.assertErrorEnvelope(response, 400, BetTestSupport.BETS_PATH);
        var list = BetTestSupport.listBets(context, userId, Map.of("page", "0", "size", "20"));
        BetTestSupport.assertListEnvelope(list);
        assertThat(BetTestSupport.json(list).get("content").size()).isZero();
        assertThat(BetTestSupport.json(list).get("totalElements").asLong()).isZero();
    }

    private static Stream<Arguments> invalidOdds() {
        return Stream.of(
                Arguments.of("exactly one", new BigDecimal("1")),
                Arguments.of("below one", new BigDecimal("0.99")),
                Arguments.of("zero", new BigDecimal("0")),
                Arguments.of("negative", new BigDecimal("-1")),
                Arguments.of("greater than one rounded to one", new BigDecimal("1.00004")));
    }

    @Test
    void should_not_use_client_supplied_user_id_as_owner_when_unknown_fields_are_accepted() throws Exception {
        var userA = UUID.randomUUID();
        var userB = UUID.randomUUID();
        var request = BetTestSupport.validCreateRequest();
        request.put("userId", userB.toString());

        var response = BetTestSupport.createBet(context, userA, request);

        assertThat(response.statusCode()).isIn(201, 400);
        if (response.statusCode() == 201) {
            BetTestSupport.assertCreatedBetResponse(response, userA, request, "100.00", "2.1000");
            var betId = BetTestSupport.responseBetId(response);
            assertThat(BetTestSupport.getBet(context, userA, betId.toString()).statusCode())
                    .isEqualTo(200);
            assertThat(BetTestSupport.getBet(context, userB, betId.toString()).statusCode())
                    .isEqualTo(404);
        } else {
            BetTestSupport.assertErrorEnvelope(response, 400, BetTestSupport.BETS_PATH);
            assertThat(BetTestSupport.json(
                    BetTestSupport.listBets(context, userA, Map.of("page", "0", "size", "20")))
                    .get("content").size()).isZero();
            assertThat(BetTestSupport.json(
                    BetTestSupport.listBets(context, userB, Map.of("page", "0", "size", "20")))
                    .get("content").size()).isZero();
        }
    }

    @Test
    void should_return_an_empty_page_for_a_user_without_bets() throws Exception {
        var userId = UUID.randomUUID();

        var response = BetTestSupport.listBets(context, userId, Map.of("page", "0", "size", "20"));

        BetTestSupport.assertListEnvelope(response);
        var body = BetTestSupport.json(response);
        assertThat(body.get("content").size()).isZero();
        assertThat(body.get("page").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(20);
        assertThat(body.get("totalElements").asLong()).isZero();
        assertThat(body.get("totalPages").asInt()).isZero();
    }

    @Test
    void should_list_multiple_bets_for_the_same_authenticated_user_and_no_other_user_bets() throws Exception {
        var userA = UUID.randomUUID();
        var userB = UUID.randomUUID();
        var firstRequest = BetTestSupport.validCreateRequest();
        firstRequest.put("notes", "first isolated bet");
        var secondRequest = BetTestSupport.validCreateRequest();
        secondRequest.put("sport", "TENNIS");
        secondRequest.put("league", "ATP");
        secondRequest.put("homeTeam", "Player A");
        secondRequest.put("awayTeam", "Player B");
        secondRequest.put("market", "MATCH_WINNER");
        secondRequest.put("selection", "Player A");
        secondRequest.put("odds", new BigDecimal("3.0000"));
        secondRequest.put("stake", new BigDecimal("50.00"));
        secondRequest.put("placedAt", "2026-07-22T20:30:00Z");
        secondRequest.put("notes", "second isolated bet");
        var otherUserRequest = BetTestSupport.validCreateRequest();
        otherUserRequest.put("notes", "must not leak");

        var first = BetTestSupport.createBet(context, userA, firstRequest);
        var second = BetTestSupport.createBet(context, userA, secondRequest);
        var other = BetTestSupport.createBet(context, userB, otherUserRequest);
        var firstId = BetTestSupport.responseBetId(first);
        var secondId = BetTestSupport.responseBetId(second);
        var otherId = BetTestSupport.responseBetId(other);

        var response = BetTestSupport.listBets(context, userA, Map.of("page", "0", "size", "20"));

        BetTestSupport.assertListEnvelope(response);
        var body = BetTestSupport.json(response);
        assertThat(body.get("content").size()).isEqualTo(2);
        assertThat(body.get("totalElements").asLong()).isEqualTo(2);
        assertThat(contentIds(body)).containsExactlyInAnyOrder(firstId, secondId);
        assertThat(contentIds(body)).doesNotContain(otherId);
        for (var item : body.get("content")) {
            if (item.get("id").asText().equals(firstId.toString())) {
                BetTestSupport.assertListItem(item, firstRequest, "100.00", "2.1000");
            } else {
                BetTestSupport.assertListItem(item, secondRequest, "50.00", "3.0000");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentedFilters")
    void should_apply_each_documented_filter_without_bypassing_ownership(
            String scenario,
            Map<String, String> filter,
            boolean matchesBothOwnedBets) throws Exception {
        var userId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var firstRequest = BetTestSupport.createRequest(
                "FOOTBALL",
                "Alpha League",
                "Team A",
                "Team B",
                "MATCH_RESULT",
                "Team A",
                new BigDecimal("2.1000"),
                new BigDecimal("100.00"),
                "2026-07-10T20:30:00Z",
                "first filter fixture");
        var secondRequest = BetTestSupport.createRequest(
                "TENNIS",
                "Beta League",
                "Team C",
                "Team D",
                "MATCH_WINNER",
                "Team C",
                new BigDecimal("3.0000"),
                new BigDecimal("50.00"),
                "2026-07-20T20:30:00Z",
                "second filter fixture");
        var otherUserRequest = BetTestSupport.createRequest(
                "FOOTBALL",
                "Alpha League",
                "Team A",
                "Team E",
                "MATCH_RESULT",
                "Team A",
                new BigDecimal("2.5000"),
                new BigDecimal("200.00"),
                "2026-07-10T20:30:00Z",
                "other user fixture");

        var firstId = BetTestSupport.responseBetId(
                BetTestSupport.createBet(context, userId, firstRequest));
        var secondId = BetTestSupport.responseBetId(
                BetTestSupport.createBet(context, userId, secondRequest));
        var otherId = BetTestSupport.responseBetId(
                BetTestSupport.createBet(context, otherUserId, otherUserRequest));
        var query = new java.util.HashMap<>(filter);
        query.put("page", "0");
        query.put("size", "20");

        var response = BetTestSupport.listBets(context, userId, query);

        BetTestSupport.assertListEnvelope(response);
        var body = BetTestSupport.json(response);
        var expected = matchesBothOwnedBets
                ? Set.of(firstId, secondId)
                : Set.of(expectedMatchingId(filter, firstId, secondId));
        assertThat(contentIds(body)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(contentIds(body)).doesNotContain(otherId);
    }

    private static Stream<Arguments> documentedFilters() {
        return Stream.of(
                Arguments.of("startDate", Map.of("startDate", "2026-07-15T00:00:00Z"), false),
                Arguments.of("endDate", Map.of("endDate", "2026-07-15T00:00:00Z"), false),
                Arguments.of("sport", Map.of("sport", "FOOTBALL"), false),
                Arguments.of("league", Map.of("league", "Alpha League"), false),
                Arguments.of("team", Map.of("team", "Team A"), false),
                Arguments.of("market", Map.of("market", "MATCH_RESULT"), false),
                Arguments.of("status", Map.of("status", "PENDING"), true),
                Arguments.of("minOdds", Map.of("minOdds", "2.2"), false),
                Arguments.of("maxOdds", Map.of("maxOdds", "2.2"), false),
                Arguments.of("minStake", Map.of("minStake", "75.00"), false),
                Arguments.of("maxStake", Map.of("maxStake", "75.00"), false));
    }

    private static UUID expectedMatchingId(Map<String, String> filter, UUID firstId, UUID secondId) {
        return filter.containsKey("startDate")
                || filter.containsKey("minOdds")
                || filter.containsKey("maxStake")
                ? secondId
                : firstId;
    }

    @Test
    void should_support_explicit_page_and_size_while_preserving_authenticated_ownership() throws Exception {
        var userId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var first = BetTestSupport.responseBetId(
                BetTestSupport.createBet(context, userId, BetTestSupport.validCreateRequest()));
        var secondRequest = BetTestSupport.validCreateRequest();
        secondRequest.put("placedAt", "2026-07-22T20:30:00Z");
        var second = BetTestSupport.responseBetId(
                BetTestSupport.createBet(context, userId, secondRequest));
        var other = BetTestSupport.responseBetId(
                BetTestSupport.createBet(context, otherUserId, BetTestSupport.validCreateRequest()));

        var response = BetTestSupport.listBets(context, userId, Map.of("page", "0", "size", "1"));

        BetTestSupport.assertListEnvelope(response);
        var body = BetTestSupport.json(response);
        assertThat(body.get("page").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(1);
        assertThat(body.get("content").size()).isEqualTo(1);
        assertThat(body.get("totalElements").asLong()).isEqualTo(2);
        assertThat(body.get("totalPages").asInt()).isEqualTo(2);
        assertThat(contentIds(body)).containsAnyOf(first, second);
        assertThat(contentIds(body)).doesNotContain(other);
    }

    @Test
    void should_return_an_owned_bet_by_id_with_the_documented_response() throws Exception {
        var userId = UUID.randomUUID();
        var request = BetTestSupport.validCreateRequest();
        var created = BetTestSupport.createBet(context, userId, request);
        var betId = BetTestSupport.responseBetId(created);

        var response = BetTestSupport.getBet(context, userId, betId.toString());

        BetTestSupport.assertRetrievedBetResponse(response, userId, request, "100.00", "2.1000");
    }

    @Test
    void should_return_404_for_missing_and_cross_user_bets_with_indistinguishable_external_errors() throws Exception {
        var userA = UUID.randomUUID();
        var userB = UUID.randomUUID();
        var otherBet = BetTestSupport.createBet(context, userB, BetTestSupport.validCreateRequest());
        var otherBetId = BetTestSupport.responseBetId(otherBet);
        var missingId = UUID.randomUUID();

        var missing = BetTestSupport.getBet(context, userA, missingId.toString());
        var crossUser = BetTestSupport.getBet(context, userA, otherBetId.toString());

        BetTestSupport.assertErrorEnvelope(missing, 404, BetTestSupport.BETS_PATH + "/" + missingId);
        BetTestSupport.assertErrorEnvelope(
                crossUser, 404, BetTestSupport.BETS_PATH + "/" + otherBetId);
        assertThat(normalizedError(missing)).isEqualTo(normalizedError(crossUser));
        assertThat(crossUser.statusCode()).isNotEqualTo(403);
        assertThat(crossUser.body()).doesNotContain(userB.toString());
    }

    private static Set<UUID> contentIds(JsonNode page) {
        var ids = new HashSet<UUID>();
        for (var item : page.get("content")) {
            ids.add(UUID.fromString(item.get("id").asText()));
        }
        return ids;
    }

    private static JsonNode normalizedError(HttpResponse<String> response) throws Exception {
        var normalized = (ObjectNode) BetTestSupport.json(response).deepCopy();
        normalized.remove("timestamp");
        normalized.remove("path");
        return normalized;
    }
}
