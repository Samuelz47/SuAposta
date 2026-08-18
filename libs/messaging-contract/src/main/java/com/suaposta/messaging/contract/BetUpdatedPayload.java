package com.suaposta.messaging.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BetUpdatedPayload(
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
        Instant placedAt,
        Instant updatedAt) {
}
