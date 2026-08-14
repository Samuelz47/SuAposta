package com.suaposta.betting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateBetCommand(
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
