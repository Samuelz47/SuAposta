package com.suaposta.betting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StakeTest {

    @Test
    void should_accept_positive_integer_stake_and_normalize_it_to_scale_two() {
        var stake = new Stake(new BigDecimal("100"));

        assertThat(stake.value()).isInstanceOf(BigDecimal.class);
        assertThat(stake.value()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(stake.value().scale()).isEqualTo(2);
    }

    @Test
    void should_accept_positive_decimal_stake_and_normalize_it_to_scale_two() {
        var stake = new Stake(new BigDecimal("25.5"));

        assertThat(stake.value()).isEqualByComparingTo(new BigDecimal("25.50"));
        assertThat(stake.value().scale()).isEqualTo(2);
    }

    @Test
    void should_round_stake_using_half_up_when_input_has_more_than_two_decimal_places() {
        var stake = new Stake(new BigDecimal("10.126"));

        assertThat(stake.value()).isEqualTo(new BigDecimal("10.13"));
        assertThat(stake.value().scale()).isEqualTo(2);
    }

    @Test
    void should_round_exact_half_up_boundary_instead_of_using_binary_floating_point() {
        var stake = new Stake(new BigDecimal("1.005"));

        assertThat(stake.value()).isEqualTo(new BigDecimal("1.01"));
    }

    @Test
    void should_reject_positive_stake_that_becomes_zero_after_normalization() {
        assertThatThrownBy(() -> new Stake(new BigDecimal("0.004")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void should_accept_positive_stake_at_half_up_boundary_when_it_normalizes_to_one_cent() {
        var stake = new Stake(new BigDecimal("0.005"));

        assertThat(stake.value()).isEqualTo(new BigDecimal("0.01"));
        assertThat(stake.value().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("invalidStakes")
    void should_reject_non_positive_stake_when_created(BigDecimal invalidStake) {
        assertThatThrownBy(() -> new Stake(invalidStake))
                .isInstanceOf(RuntimeException.class);
    }

    private static Stream<Arguments> invalidStakes() {
        return Stream.of(
                Arguments.of(new BigDecimal("0")),
                Arguments.of(new BigDecimal("0.00")),
                Arguments.of(new BigDecimal("-0.01")),
                Arguments.of(new BigDecimal("-100")));
    }
}
