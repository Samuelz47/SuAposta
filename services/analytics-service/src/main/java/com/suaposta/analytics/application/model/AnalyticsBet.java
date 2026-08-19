package com.suaposta.analytics.application.model;

import com.suaposta.messaging.contract.BetStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AnalyticsBet(
        UUID id,
        UUID betId,
        UUID userId,
        String sport,
        String league,
        String homeTeam,
        String awayTeam,
        String market,
        String selection,
        BigDecimal odds,
        BigDecimal stake,
        BetStatus status,
        BigDecimal profit,
        BigDecimal returnAmount,
        Instant placedAt,
        Instant settledAt,
        Instant createdAt,
        Instant updatedAt) {
}
