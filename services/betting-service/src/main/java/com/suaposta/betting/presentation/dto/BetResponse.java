package com.suaposta.betting.presentation.dto;

import com.suaposta.betting.domain.model.BetStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BetResponse(
        UUID id,
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
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
