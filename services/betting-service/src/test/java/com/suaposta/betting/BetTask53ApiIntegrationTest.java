package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;

class BetTask53ApiIntegrationTest {

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
    void should_update_all_documented_mutable_fields_of_an_owned_pending_bet() throws Exception {
        var userId = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, userId, BetTestSupport.validCreateRequest());
        var betId = BetTestSupport.responseBetId(created);
        var before = BetTestSupport.json(created);
        var update = BetTask53HttpTestSupport.validUpdateRequest();

        var response = BetTask53HttpTestSupport.updateBet(context, userId, betId.toString(), update);

        BetTask53HttpTestSupport.assertPendingUpdateResponse(response, userId, betId, update, before);
        BetTask53HttpTestSupport.assertPendingUpdateResponse(
                BetTestSupport.getBet(context, userId, betId.toString()), userId, betId, update, before);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUpdateFinancialInputs")
    void should_reject_invalid_update_financial_input_without_partial_mutation(
            String scenario, String field, BigDecimal value) throws Exception {
        var userId = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, userId, BetTestSupport.validCreateRequest());
        var betId = BetTestSupport.responseBetId(created);
        var before = BetTestSupport.json(BetTestSupport.getBet(context, userId, betId.toString()));
        var update = BetTask53HttpTestSupport.validUpdateRequest();
        update.put(field, value);

        var response = BetTask53HttpTestSupport.updateBet(context, userId, betId.toString(), update);

        BetTask53HttpTestSupport.assertError(response, 400, BetTestSupport.BETS_PATH + "/" + betId);
        assertThat(BetTask53HttpTestSupport.json(
                BetTestSupport.getBet(context, userId, betId.toString())))
                .isEqualTo(before);
    }

    private static Stream<Arguments> invalidUpdateFinancialInputs() {
        return Stream.of(
                Arguments.of("zero stake", "stake", new BigDecimal("0")),
                Arguments.of("negative stake", "stake", new BigDecimal("-0.01")),
                Arguments.of("stake rounds to zero", "stake", new BigDecimal("0.004")),
                Arguments.of("odds equal one", "odds", new BigDecimal("1")),
                Arguments.of("odds below one", "odds", new BigDecimal("0.99")),
                Arguments.of("odds rounds to one", "odds", new BigDecimal("1.00004")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("finalStatuses")
    void should_return_409_and_preserve_every_final_bet_when_update_is_attempted(
            String status) throws Exception {
        var userId = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, userId, BetTestSupport.validCreateRequest());
        var betId = BetTestSupport.responseBetId(created);
        var settled = BetTask53HttpTestSupport.settleBet(
                context, userId, betId.toString(), settlementFor(status));
        assertThat(settled.statusCode()).isEqualTo(200);
        var before = snapshot(BetTask53HttpTestSupport.json(settled));

        var response = BetTask53HttpTestSupport.updateBet(
                context, userId, betId.toString(), BetTask53HttpTestSupport.validUpdateRequest());

        BetTask53HttpTestSupport.assertError(response, 409, BetTestSupport.BETS_PATH + "/" + betId);
        assertThat(BetTask53ApiIntegrationTest.snapshot(
                body(BetTestSupport.getBet(context, userId, betId.toString()))))
                .isEqualTo(before);
    }

    private static Stream<Arguments> finalStatuses() {
        return Stream.of(
                Arguments.of("WON"),
                Arguments.of("LOST"),
                Arguments.of("VOID"),
                Arguments.of("CASHOUT"),
                Arguments.of("CANCELLED"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("successfulSettlements")
    void should_settle_each_supported_status_with_domain_derived_financial_values(
            String scenario, String status, String returnAmount, String profit, String stake) throws Exception {
        var userId = UUID.randomUUID();
        var create = BetTestSupport.validCreateRequest();
        create.put("stake", new BigDecimal(stake));
        var created = BetTestSupport.createBet(context, userId, create);
        var betId = BetTestSupport.responseBetId(created);
        var before = BetTestSupport.json(created);
        var request = "CASHOUT".equals(status)
                ? BetTask53HttpTestSupport.cashout(returnAmount)
                : settlementFor(status);

        var response = BetTask53HttpTestSupport.settleBet(context, userId, betId.toString(), request);

        BetTask53HttpTestSupport.assertSettledResponse(
                response, userId, betId, status, returnAmount, profit, stake, before);
    }

    private static Stream<Arguments> successfulSettlements() {
        return Stream.of(
                Arguments.of("WON", "WON", "210.00", "110.00", "100.00"),
                Arguments.of("LOST", "LOST", "0.00", "-100.00", "100.00"),
                Arguments.of("VOID", "VOID", "100.00", "0.00", "100.00"),
                Arguments.of("CANCELLED", "CANCELLED", "100.00", "0.00", "100.00"),
                Arguments.of("CASHOUT positive", "CASHOUT", "130.00", "30.00", "100.00"),
                Arguments.of("CASHOUT negative", "CASHOUT", "80.00", "-20.00", "100.00"),
                Arguments.of("CASHOUT break even", "CASHOUT", "100.00", "0.00", "100.00"),
                Arguments.of("CASHOUT rounds money", "CASHOUT", "80.13", "-19.87", "100.00"));
    }

    @Test
    void should_reject_cashout_without_return_amount_without_mutating_the_pending_bet() throws Exception {
        var userId = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, userId, BetTestSupport.validCreateRequest());
        var betId = BetTestSupport.responseBetId(created);
        var before = BetTestSupport.json(BetTestSupport.getBet(context, userId, betId.toString()));

        var response = BetTask53HttpTestSupport.settleBet(
                context, userId, betId.toString(), BetTask53HttpTestSupport.settlement("CASHOUT"));

        BetTask53HttpTestSupport.assertError(
                response, 400, BetTestSupport.BETS_PATH + "/" + betId + "/settle");
        assertThat(BetTask53ApiIntegrationTest.body(
                BetTestSupport.getBet(context, userId, betId.toString()))).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("repeatedSettlementCases")
    void should_return_409_and_preserve_result_for_repeated_or_cross_final_settlement(
            String firstStatus, String secondStatus) throws Exception {
        var userId = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, userId, BetTestSupport.validCreateRequest());
        var betId = BetTestSupport.responseBetId(created);
        var first = BetTask53HttpTestSupport.settleBet(
                context, userId, betId.toString(), settlementFor(firstStatus));
        assertThat(first.statusCode()).isEqualTo(200);
        var before = snapshot(body(first));

        var response = BetTask53HttpTestSupport.settleBet(
                context, userId, betId.toString(), settlementFor(secondStatus));

        BetTask53HttpTestSupport.assertError(
                response, 409, BetTestSupport.BETS_PATH + "/" + betId + "/settle");
        assertThat(snapshot(body(BetTestSupport.getBet(context, userId, betId.toString()))))
                .isEqualTo(before);
    }

    private static Stream<Arguments> repeatedSettlementCases() {
        return Stream.of(
                Arguments.of("WON", "WON"),
                Arguments.of("WON", "LOST"),
                Arguments.of("LOST", "WON"),
                Arguments.of("VOID", "WON"),
                Arguments.of("CASHOUT", "CASHOUT"),
                Arguments.of("CANCELLED", "WON"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIdentityOperations")
    void should_return_401_for_missing_or_malformed_identity_on_task_53_endpoints(
            String scenario, String operation, String identity) throws Exception {
        var betId = UUID.randomUUID().toString();
        HttpResponse<String> response = operation.equals("update")
                ? BetTask53HttpTestSupport.updateBet(
                        context, identity, betId, BetTask53HttpTestSupport.validUpdateRequest())
                : BetTask53HttpTestSupport.settleBet(
                        context, identity, betId, settlementFor("WON"));
        var path = operation.equals("update")
                ? BetTestSupport.BETS_PATH + "/" + betId
                : BetTestSupport.BETS_PATH + "/" + betId + "/settle";
        BetTask53HttpTestSupport.assertError(response, 401, path);
    }

    private static Stream<Arguments> invalidIdentityOperations() {
        return Stream.of(
                Arguments.of("update missing", "update", null),
                Arguments.of("update malformed", "update", "not-a-user-uuid"),
                Arguments.of("settle missing", "settle", null),
                Arguments.of("settle malformed", "settle", "not-a-user-uuid"));
    }

    @Test
    void should_keep_missing_and_cross_user_update_errors_indistinguishable() throws Exception {
        var owner = UUID.randomUUID();
        var requester = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, owner, BetTestSupport.validCreateRequest());
        var foreignBetId = BetTestSupport.responseBetId(created);
        var missingBetId = UUID.randomUUID().toString();

        var missing = BetTask53HttpTestSupport.updateBet(
                context, requester, missingBetId, BetTask53HttpTestSupport.validUpdateRequest());
        var crossUser = BetTask53HttpTestSupport.updateBet(
                context, requester, foreignBetId.toString(), BetTask53HttpTestSupport.validUpdateRequest());

        BetTask53HttpTestSupport.assertError(missing, 404, BetTestSupport.BETS_PATH + "/" + missingBetId);
        BetTask53HttpTestSupport.assertError(
                crossUser, 404, BetTestSupport.BETS_PATH + "/" + foreignBetId);
        assertThat(BetTask53HttpTestSupport.normalizedError(missing))
                .isEqualTo(BetTask53HttpTestSupport.normalizedError(crossUser));
        assertThat(crossUser.body()).doesNotContain(owner.toString());
    }

    @Test
    void should_keep_missing_and_cross_user_settlement_errors_indistinguishable() throws Exception {
        var owner = UUID.randomUUID();
        var requester = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, owner, BetTestSupport.validCreateRequest());
        var foreignBetId = BetTestSupport.responseBetId(created);
        var missingBetId = UUID.randomUUID().toString();

        var missing = BetTask53HttpTestSupport.settleBet(
                context, requester, missingBetId, settlementFor("WON"));
        var crossUser = BetTask53HttpTestSupport.settleBet(
                context, requester, foreignBetId.toString(), settlementFor("WON"));

        BetTask53HttpTestSupport.assertError(
                missing, 404, BetTestSupport.BETS_PATH + "/" + missingBetId + "/settle");
        BetTask53HttpTestSupport.assertError(
                crossUser, 404, BetTestSupport.BETS_PATH + "/" + foreignBetId + "/settle");
        assertThat(BetTask53HttpTestSupport.normalizedError(missing))
                .isEqualTo(BetTask53HttpTestSupport.normalizedError(crossUser));
        assertThat(crossUser.body()).doesNotContain(owner.toString());
    }

    @Test
    void should_not_allow_client_fields_to_control_ownership_or_settlement_results() throws Exception {
        var owner = UUID.randomUUID();
        var attacker = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, owner, BetTestSupport.validCreateRequest());
        var betId = BetTestSupport.responseBetId(created);
        var update = BetTask53HttpTestSupport.validUpdateRequest();
        update.put("id", UUID.randomUUID().toString());
        update.put("userId", attacker.toString());
        update.put("status", "WON");
        update.put("profit", new BigDecimal("999999.99"));
        update.put("returnAmount", new BigDecimal("999999.99"));
        update.put("settledAt", "2000-01-01T00:00:00Z");
        update.put("createdAt", "2000-01-01T00:00:00Z");

        var response = BetTask53HttpTestSupport.updateBet(context, owner, betId.toString(), update);

        assertThat(response.statusCode()).isIn(200, 400);
        var persisted = BetTestSupport.json(BetTestSupport.getBet(context, owner, betId.toString()));
        assertThat(persisted.get("userId").asText()).isEqualTo(owner.toString());
        assertThat(persisted.get("status").asText()).isEqualTo("PENDING");
        assertThat(persisted.get("profit").isNull()).isTrue();
        assertThat(persisted.get("returnAmount").isNull()).isTrue();
        assertThat(persisted.get("settledAt").isNull()).isTrue();
        assertThat(persisted.get("createdAt").asText()).isEqualTo(
                BetTask53ApiIntegrationTest.body(created).get("createdAt").asText());
    }

    @Test
    void should_reject_or_ignore_pending_to_pending_without_changing_pending_state() throws Exception {
        var userId = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, userId, BetTestSupport.validCreateRequest());
        var betId = BetTestSupport.responseBetId(created);
        var before = body(BetTestSupport.getBet(context, userId, betId.toString()));

        var response = BetTask53HttpTestSupport.settleBet(
                context, userId, betId.toString(), settlementFor("PENDING"));

        assertThat(response.statusCode()).isIn(400, 409);
        assertThat(snapshot(body(BetTestSupport.getBet(context, userId, betId.toString()))))
                .isEqualTo(snapshot(before));
    }

    @Test
    void should_not_allow_settlement_request_fields_to_control_settled_at_or_profit() throws Exception {
        var userId = UUID.randomUUID();
        var created = BetTestSupport.createBet(context, userId, BetTestSupport.validCreateRequest());
        var betId = BetTestSupport.responseBetId(created);
        var request = settlementFor("WON");
        request.put("settledAt", "2000-01-01T00:00:00Z");
        request.put("profit", new BigDecimal("999999.99"));

        var response = BetTask53HttpTestSupport.settleBet(context, userId, betId.toString(), request);

        assertThat(response.statusCode()).isIn(200, 400);
        if (response.statusCode() == 200) {
            var result = body(response);
            assertThat(result.get("profit").decimalValue()).isEqualByComparingTo("110.00");
            assertThat(result.get("settledAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
            assertThat(result.get("userId").asText()).isEqualTo(userId.toString());
            assertThat(body(BetTestSupport.getBet(context, userId, betId.toString())).get("profit")
                    .decimalValue()).isEqualByComparingTo("110.00");
        } else {
            BetTask53HttpTestSupport.assertError(
                    response, 400, BetTestSupport.BETS_PATH + "/" + betId + "/settle");
        }
    }

    private static ObjectNode settlementFor(String status) {
        return "CASHOUT".equals(status)
                ? BetTask53HttpTestSupport.cashout("130.00")
                : BetTask53HttpTestSupport.settlement(status);
    }

    private static JsonNode body(HttpResponse<String> response) throws Exception {
        return BetTask53HttpTestSupport.json(response);
    }

    private static JsonNode snapshot(JsonNode body) {
        return body.deepCopy();
    }
}
