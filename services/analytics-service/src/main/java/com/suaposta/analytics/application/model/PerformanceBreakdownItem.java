package com.suaposta.analytics.application.model;

import java.math.BigDecimal;

public record PerformanceBreakdownItem(
        String name,
        BigDecimal totalStake,
        BigDecimal profit,
        BigDecimal roi,
        BigDecimal yield,
        BigDecimal winRate,
        BigDecimal avgOdds,
        BigDecimal drawdown,
        long betsCount,
        long pendingCount,
        long wonCount,
        long lostCount,
        long voidCount,
        long cashoutCount,
        long cancelledCount) {
}
