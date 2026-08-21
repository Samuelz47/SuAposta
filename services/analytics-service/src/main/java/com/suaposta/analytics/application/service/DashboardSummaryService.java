package com.suaposta.analytics.application.service;

import com.suaposta.analytics.application.model.AnalyticsBet;
import com.suaposta.analytics.application.model.DashboardFilters;
import com.suaposta.analytics.application.model.DashboardSummary;
import com.suaposta.analytics.application.port.out.AnalyticsBetRepository;
import com.suaposta.messaging.contract.BetStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DashboardSummaryService {

    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal ZERO_ODDS = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final List<BetStatus> PERFORMANCE_STATUSES = List.of(
            BetStatus.WON, BetStatus.LOST, BetStatus.CASHOUT);

    private final AnalyticsBetRepository analyticsBetRepository;

    public DashboardSummaryService(AnalyticsBetRepository analyticsBetRepository) {
        this.analyticsBetRepository = analyticsBetRepository;
    }

    public DashboardSummary getDashboard(UUID userId, DashboardFilters filters) {
        var bets = analyticsBetRepository.findDashboardBets(userId, filters);
        var performanceBets = bets.stream()
                .filter(bet -> PERFORMANCE_STATUSES.contains(bet.status()))
                .toList();

        var totalStake = performanceBets.stream()
                .map(AnalyticsBet::stake)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalProfit = performanceBets.stream()
                .map(AnalyticsBet::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var wonBets = count(bets, BetStatus.WON);
        var lostBets = count(bets, BetStatus.LOST);
        var drawdown = drawdown(performanceBets);

        return new DashboardSummary(
                money(totalStake),
                money(totalProfit),
                percentage(totalProfit, totalStake),
                percentage(totalProfit, totalStake),
                winRate(wonBets, lostBets),
                averageOdds(performanceBets),
                bets.size(),
                wonBets,
                lostBets,
                count(bets, BetStatus.VOID),
                count(bets, BetStatus.CASHOUT),
                count(bets, BetStatus.CANCELLED),
                drawdown.maxDrawdown(),
                drawdown.currentDrawdown());
    }

    private static long count(List<AnalyticsBet> bets, BetStatus status) {
        return bets.stream().filter(bet -> bet.status() == status).count();
    }

    private static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return ZERO_MONEY;
        }
        return numerator.multiply(HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal winRate(long wonBets, long lostBets) {
        var resolved = wonBets + lostBets;
        if (resolved == 0) {
            return ZERO_MONEY;
        }
        return BigDecimal.valueOf(wonBets)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(resolved), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageOdds(List<AnalyticsBet> performanceBets) {
        if (performanceBets.isEmpty()) {
            return ZERO_ODDS;
        }
        var total = performanceBets.stream()
                .map(AnalyticsBet::odds)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(performanceBets.size()), 4, RoundingMode.HALF_UP);
    }

    private static Drawdown drawdown(List<AnalyticsBet> performanceBets) {
        var cumulativeProfit = BigDecimal.ZERO;
        var peak = BigDecimal.ZERO;
        var maxDrawdown = BigDecimal.ZERO;
        var ordered = performanceBets.stream()
                .sorted(Comparator.comparing(AnalyticsBet::settledAt).thenComparing(AnalyticsBet::betId))
                .toList();
        for (var bet : ordered) {
            cumulativeProfit = cumulativeProfit.add(bet.profit());
            peak = peak.max(cumulativeProfit);
            var drawdown = peak.subtract(cumulativeProfit);
            maxDrawdown = maxDrawdown.max(drawdown);
        }
        return new Drawdown(money(maxDrawdown), money(peak.subtract(cumulativeProfit)));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record Drawdown(BigDecimal maxDrawdown, BigDecimal currentDrawdown) {
    }
}
