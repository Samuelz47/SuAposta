package com.suaposta.analytics.application.model;

import com.suaposta.messaging.contract.BetStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record DashboardFilters(
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
}
