package com.suaposta.betting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OddsTest {

    @Test
    void should_accept_odds_greater_than_one_and_normalize_them_to_scale_four() {
        var odds = new Odds(new BigDecimal("2.1"));

        assertThat(odds.value()).isInstanceOf(BigDecimal.class);
        assertThat(odds.value()).isEqualByComparingTo(new BigDecimal("2.1000"));
        assertThat(odds.value().scale()).isEqualTo(4);
    }

    @Test
    void should_accept_decimal_odds_and_preserve_scale_four() {
        var odds = new Odds(new BigDecimal("1.85"));

        assertThat(odds.value()).isEqualByComparingTo(new BigDecimal("1.8500"));
        assertThat(odds.value().scale()).isEqualTo(4);
    }

    @Test
    void should_round_odds_using_half_up_when_input_has_more_than_four_decimal_places() {
        var odds = new Odds(new BigDecimal("2.12555"));

        assertThat(odds.value()).isEqualTo(new BigDecimal("2.1256"));
        assertThat(odds.value().scale()).isEqualTo(4);
    }

    @Test
    void should_round_odds_at_the_half_up_boundary_without_binary_floating_point() {
        var odds = new Odds(new BigDecimal("1.00005"));

        assertThat(odds.value()).isEqualTo(new BigDecimal("1.0001"));
    }

    @Test
    void should_reject_odds_greater_than_one_that_become_one_after_normalization() {
        assertThatThrownBy(() -> new Odds(new BigDecimal("1.00004")))
                .isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidOdds")
    void should_reject_odds_not_strictly_greater_than_one_when_created(BigDecimal invalidOdds) {
        assertThatThrownBy(() -> new Odds(invalidOdds))
                .isInstanceOf(RuntimeException.class);
    }

    private static Stream<Arguments> invalidOdds() {
        return Stream.of(
                Arguments.of(new BigDecimal("1")),
                Arguments.of(new BigDecimal("1.0000")),
                Arguments.of(new BigDecimal("0.99")),
                Arguments.of(new BigDecimal("0")),
                Arguments.of(new BigDecimal("-1")));
    }
}
