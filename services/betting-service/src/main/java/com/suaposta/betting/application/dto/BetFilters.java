package com.suaposta.betting.application.dto;

import com.suaposta.betting.domain.model.BetStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record BetFilters(
        Instant startDate,
        Instant endDate,
        String sport,
        String league,
        String team,
        String market,
        BetStatus status,
        BigDecimal minOdds,
        BigDecimal maxOdds,
        BigDecimal minStake,
        BigDecimal maxStake) {

    public static BetFilters empty() {
        return new BetFilters(null, null, null, null, null, null, null, null, null, null, null);
    }
}
