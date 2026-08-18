package com.suaposta.messaging.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BetSettledPayload(
        UUID betId,
        UUID userId,
        BetStatus status,
        BigDecimal odds,
        BigDecimal stake,
        BigDecimal profit,
        BigDecimal returnAmount,
        Instant settledAt) {
}
