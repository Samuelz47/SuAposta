package com.suaposta.betting.presentation.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateBetRequest(
        String sport,
        String league,
        String homeTeam,
        String awayTeam,
        String market,
        String selection,
        BigDecimal odds,
        BigDecimal stake,
        Instant placedAt,
        String notes) {
}
