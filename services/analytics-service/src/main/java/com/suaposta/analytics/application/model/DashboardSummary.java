package com.suaposta.analytics.application.model;

import java.math.BigDecimal;

public record DashboardSummary(
        BigDecimal totalStake,
        BigDecimal totalProfit,
        BigDecimal roi,
        BigDecimal yield,
        BigDecimal winRate,
        BigDecimal averageOdds,
        long betsCount,
        long wonBets,
        long lostBets,
        long voidBets,
        long cashoutBets,
        long cancelledBets,
        BigDecimal maxDrawdown,
        BigDecimal currentDrawdown) {
}
