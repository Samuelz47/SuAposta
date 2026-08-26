package com.suaposta.analytics.application.service;

import com.suaposta.analytics.application.model.AnalyticsBet;
import com.suaposta.analytics.application.model.BankrollEvolution;
import com.suaposta.analytics.application.model.BankrollEvolutionFilters;
import com.suaposta.analytics.application.model.BankrollEvolutionPoint;
import com.suaposta.analytics.application.port.out.AnalyticsBetRepository;
import com.suaposta.messaging.contract.BetStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class BankrollEvolutionService {

    private static final List<BetStatus> PERFORMANCE_STATUSES = List.of(
            BetStatus.WON, BetStatus.LOST, BetStatus.CASHOUT);

    private final AnalyticsBetRepository analyticsBetRepository;

    public BankrollEvolutionService(AnalyticsBetRepository analyticsBetRepository) {
        this.analyticsBetRepository = analyticsBetRepository;
    }

    public BankrollEvolution getBankrollEvolution(UUID userId, BankrollEvolutionFilters filters) {
        var orderedPerformanceBets = analyticsBetRepository.findBankrollEvolutionBets(userId, filters).stream()
                .filter(bet -> PERFORMANCE_STATUSES.contains(bet.status()))
                .sorted(Comparator.comparing(AnalyticsBet::settledAt).thenComparing(AnalyticsBet::betId))
                .toList();
        var cumulativeProfit = BigDecimal.ZERO;
        var points = new java.util.ArrayList<BankrollEvolutionPoint>();
        for (var bet : orderedPerformanceBets) {
            cumulativeProfit = cumulativeProfit.add(bet.profit());
            points.add(new BankrollEvolutionPoint(
                    bet.settledAt().atOffset(ZoneOffset.UTC).toLocalDate(),
                    money(bet.profit()),
                    money(cumulativeProfit),
                    money(cumulativeProfit)));
        }
        return new BankrollEvolution(List.copyOf(points));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
