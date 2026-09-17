package com.suaposta.betting.presentation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBetRequest(
        @NotBlank String sport,
        @NotBlank String league,
        @NotBlank String homeTeam,
        @NotBlank String awayTeam,
        @NotBlank String market,
        @NotBlank String selection,
        @NotNull BigDecimal odds,
        @NotNull BigDecimal stake,
        @NotNull Instant placedAt,
        String notes) {
}
