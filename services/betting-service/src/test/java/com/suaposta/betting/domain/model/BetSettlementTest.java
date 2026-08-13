package com.suaposta.betting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BetSettlementTest {

    @Test
    void should_start_a_valid_bet_as_pending_without_financial_results_or_settlement_time() {
        var bet = newBet("100.00", "2.1000");

        assertThat(bet.status()).isEqualTo(BetStatus.PENDING);
        assertThat(bet.profit()).isNull();
        assertThat(bet.returnAmount()).isNull();
        assertThat(bet.settledAt()).isNull();
    }

    @Test
    void should_settle_pending_bet_as_won_using_stake_times_odds() {
        var bet = newBet("100.00", "2.1000");

        bet.settle(BetStatus.WON);

        assertSettlement(bet, BetStatus.WON, "110.00", "210.00");
    }

    @Test
    void should_settle_won_bet_with_decimal_inputs_and_monetary_rounding() {
        var bet = newBet("25.55", "1.8556");

        bet.settle(BetStatus.WON);

        assertSettlement(bet, BetStatus.WON, "21.86", "47.41");
    }

    @Test
    void should_settle_pending_bet_as_lost_with_negative_stake_profit_and_zero_return() {
        var bet = newBet("25.55", "1.8500");

        bet.settle(BetStatus.LOST);

        assertSettlement(bet, BetStatus.LOST, "-25.55", "0.00");
    }

    @Test
    void should_settle_pending_bet_as_void_with_stake_returned_and_zero_profit() {
        var bet = newBet("25.55", "1.8500");

        bet.settle(BetStatus.VOID);

        assertSettlement(bet, BetStatus.VOID, "0.00", "25.55");
    }

    @Test
    void should_settle_pending_bet_as_cancelled_with_stake_returned_and_zero_profit() {
        var bet = newBet("25.55", "1.8500");

        bet.settle(BetStatus.CANCELLED);

        assertSettlement(bet, BetStatus.CANCELLED, "0.00", "25.55");
    }

    @Test
    void should_settle_cashout_with_positive_profit_derived_from_return_amount() {
        var bet = newBet("100.00", "2.1000");

        bet.settle(BetStatus.CASHOUT, new BigDecimal("130.00"));

        assertSettlement(bet, BetStatus.CASHOUT, "30.00", "130.00");
    }

    @Test
    void should_settle_cashout_with_negative_profit_derived_from_return_amount() {
        var bet = newBet("100.00", "2.1000");

        bet.settle(BetStatus.CASHOUT, new BigDecimal("80.00"));

        assertSettlement(bet, BetStatus.CASHOUT, "-20.00", "80.00");
    }

    @Test
    void should_settle_cashout_with_zero_profit_when_return_amount_equals_stake() {
        var bet = newBet("100.00", "2.1000");

        bet.settle(BetStatus.CASHOUT, new BigDecimal("100.00"));

        assertSettlement(bet, BetStatus.CASHOUT, "0.00", "100.00");
    }

    @Test
    void should_normalize_cashout_return_and_profit_to_scale_two_using_half_up() {
        var bet = newBet("10.00", "2.1000");

        bet.settle(BetStatus.CASHOUT, new BigDecimal("10.005"));

        assertSettlement(bet, BetStatus.CASHOUT, "0.01", "10.01");
    }

    @Test
    void should_reject_cashout_without_an_explicit_return_amount() {
        var bet = newBet("100.00", "2.1000");

        assertThatThrownBy(() -> bet.settle(BetStatus.CASHOUT, null))
                .isInstanceOf(RuntimeException.class);

        assertThat(bet.status()).isEqualTo(BetStatus.PENDING);
        assertThat(bet.profit()).isNull();
        assertThat(bet.returnAmount()).isNull();
    }

    @Test
    void should_reject_cashout_without_return_amount_argument_and_preserve_pending_state() {
        var bet = newBet("100.00", "2.1000");

        assertThatThrownBy(() -> bet.settle(BetStatus.CASHOUT))
                .isInstanceOf(RuntimeException.class);

        assertThat(bet.status()).isEqualTo(BetStatus.PENDING);
        assertThat(bet.profit()).isNull();
        assertThat(bet.returnAmount()).isNull();
    }

    @Test
    void should_reject_pending_to_pending_settlement_without_partial_mutation() {
        var bet = newBet("100.00", "2.1000");

        assertThatThrownBy(() -> bet.settle(BetStatus.PENDING))
                .isInstanceOf(RuntimeException.class);

        assertThat(bet.status()).isEqualTo(BetStatus.PENDING);
        assertThat(bet.profit()).isNull();
        assertThat(bet.returnAmount()).isNull();
        assertThat(bet.settledAt()).isNull();
    }

    @ParameterizedTest
    @MethodSource("validFinalStatuses")
    void should_allow_every_documented_final_status_from_pending(BetStatus status) {
        var bet = newBet("100.00", "2.1000");

        settleWithRequiredInput(bet, status);

        assertThat(bet.status()).isEqualTo(status);
        assertThat(bet.profit()).isNotNull();
        assertThat(bet.returnAmount()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("validFinalStatuses")
    void should_reject_repeated_settlement_of_the_same_final_status(BetStatus status) {
        var bet = newBet("100.00", "2.1000");
        settleWithRequiredInput(bet, status);
        var originalProfit = bet.profit();
        var originalReturnAmount = bet.returnAmount();

        assertThatThrownBy(() -> settleWithRequiredInput(bet, status))
                .isInstanceOf(RuntimeException.class);

        assertThat(bet.status()).isEqualTo(status);
        assertThat(bet.profit()).isEqualTo(originalProfit);
        assertThat(bet.returnAmount()).isEqualTo(originalReturnAmount);
    }

    @ParameterizedTest
    @MethodSource("finalStatusesSettledBeforeAttemptedWon")
    void should_reject_transition_from_any_final_status_to_won(BetStatus initialStatus) {
        var bet = newBet("100.00", "2.1000");
        settleWithRequiredInput(bet, initialStatus);
        var originalProfit = bet.profit();
        var originalReturnAmount = bet.returnAmount();

        assertThatThrownBy(() -> bet.settle(BetStatus.WON))
                .isInstanceOf(RuntimeException.class);

        assertThat(bet.status()).isEqualTo(initialStatus);
        assertThat(bet.profit()).isEqualTo(originalProfit);
        assertThat(bet.returnAmount()).isEqualTo(originalReturnAmount);
    }

    @Test
    void should_reject_cashout_when_bet_is_already_won_without_partial_mutation() {
        var bet = newBet("100.00", "2.1000");
        bet.settle(BetStatus.WON);
        var originalProfit = bet.profit();
        var originalReturnAmount = bet.returnAmount();

        assertThatThrownBy(() -> bet.settle(BetStatus.CASHOUT, new BigDecimal("130.00")))
                .isInstanceOf(RuntimeException.class);

        assertThat(bet.status()).isEqualTo(BetStatus.WON);
        assertThat(bet.profit()).isEqualTo(originalProfit);
        assertThat(bet.returnAmount()).isEqualTo(originalReturnAmount);
    }

    private static Stream<Arguments> validFinalStatuses() {
        return Stream.of(
                Arguments.of(BetStatus.WON),
                Arguments.of(BetStatus.LOST),
                Arguments.of(BetStatus.VOID),
                Arguments.of(BetStatus.CASHOUT),
                Arguments.of(BetStatus.CANCELLED));
    }

    private static Stream<Arguments> finalStatusesSettledBeforeAttemptedWon() {
        return validFinalStatuses();
    }

    private static Bet newBet(String stake, String odds) {
        return new Bet(new Stake(new BigDecimal(stake)), new Odds(new BigDecimal(odds)));
    }

    private static void settleWithRequiredInput(Bet bet, BetStatus status) {
        if (status == BetStatus.CASHOUT) {
            bet.settle(status, new BigDecimal("130.00"));
        } else {
            bet.settle(status);
        }
    }

    private static void assertSettlement(
            Bet bet,
            BetStatus expectedStatus,
            String expectedProfit,
            String expectedReturnAmount) {
        assertThat(bet.status()).isEqualTo(expectedStatus);
        assertThat(bet.profit()).isEqualTo(new BigDecimal(expectedProfit));
        assertThat(bet.returnAmount()).isEqualTo(new BigDecimal(expectedReturnAmount));
        assertThat(bet.profit().scale()).isEqualTo(2);
        assertThat(bet.returnAmount().scale()).isEqualTo(2);
        assertThat(bet.profit()).isInstanceOf(BigDecimal.class);
        assertThat(bet.returnAmount()).isInstanceOf(BigDecimal.class);
    }
}
