package com.suaposta.analytics.application.service;

import com.suaposta.analytics.application.model.AnalyticsBet;
import com.suaposta.analytics.application.model.PerformanceBreakdown;
import com.suaposta.analytics.application.model.PerformanceBreakdownFilters;
import com.suaposta.analytics.application.model.PerformanceBreakdownGroupBy;
import com.suaposta.analytics.application.model.PerformanceBreakdownItem;
import com.suaposta.analytics.application.port.out.AnalyticsBetRepository;
import com.suaposta.messaging.contract.BetStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class PerformanceBreakdownService {

    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal ZERO_ODDS = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final List<BetStatus> PERFORMANCE_STATUSES = List.of(
            BetStatus.WON, BetStatus.LOST, BetStatus.CASHOUT);

    private final AnalyticsBetRepository analyticsBetRepository;

    public PerformanceBreakdownService(AnalyticsBetRepository analyticsBetRepository) {
        this.analyticsBetRepository = analyticsBetRepository;
    }

    public PerformanceBreakdown getPerformanceBreakdown(
            UUID userId, PerformanceBreakdownGroupBy groupBy, PerformanceBreakdownFilters filters) {
        var groups = group(analyticsBetRepository.findPerformanceBreakdownBets(userId, filters), groupBy);
        var items = groups.entrySet().stream()
                .map(entry -> item(entry.getKey(), entry.getValue()))
                .toList();
        return new PerformanceBreakdown(groupBy, items);
    }

    private static Map<String, List<AnalyticsBet>> group(
            List<AnalyticsBet> bets, PerformanceBreakdownGroupBy groupBy) {
        Map<String, List<AnalyticsBet>> groups = new TreeMap<>();
        for (var bet : bets) {
            for (var name : groupNames(bet, groupBy)) {
                groups.computeIfAbsent(name, ignored -> new ArrayList<>()).add(bet);
            }
        }
        return groups;
    }

    private static List<String> groupNames(AnalyticsBet bet, PerformanceBreakdownGroupBy groupBy) {
        return switch (groupBy) {
            case SPORT -> validName(bet.sport());
            case LEAGUE -> validName(bet.league());
            case MARKET -> validName(bet.market());
            case TEAM -> teamNames(bet);
            case DAY -> List.of(bet.placedAt().atOffset(ZoneOffset.UTC).toLocalDate().toString());
            case MONTH -> List.of(bet.placedAt().atOffset(ZoneOffset.UTC).toLocalDate()
                    .withDayOfMonth(1).toString().substring(0, 7));
            case WEEK -> List.of(isoWeek(bet));
        };
    }

    private static List<String> teamNames(AnalyticsBet bet) {
        var names = new LinkedHashSet<String>();
        addValidName(names, bet.homeTeam());
        addValidName(names, bet.awayTeam());
        return List.copyOf(names);
    }

    private static List<String> validName(String name) {
        return name == null || name.isBlank() ? List.of() : List.of(name);
    }

    private static void addValidName(LinkedHashSet<String> names, String name) {
        if (name != null && !name.isBlank()) {
            names.add(name);
        }
    }

    private static String isoWeek(AnalyticsBet bet) {
        var date = bet.placedAt().atOffset(ZoneOffset.UTC).toLocalDate();
        var fields = WeekFields.ISO;
        var weekBasedYear = date.get(fields.weekBasedYear());
        var week = date.get(fields.weekOfWeekBasedYear());
        return "%04d-W%02d".formatted(weekBasedYear, week);
    }

    private static PerformanceBreakdownItem item(String name, List<AnalyticsBet> bets) {
        var performanceBets = bets.stream()
                .filter(bet -> PERFORMANCE_STATUSES.contains(bet.status()))
                .toList();
        var totalStake = performanceBets.stream()
                .map(AnalyticsBet::stake)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var profit = performanceBets.stream()
                .map(AnalyticsBet::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var wonCount = count(bets, BetStatus.WON);
        var lostCount = count(bets, BetStatus.LOST);
        var roi = percentage(profit, totalStake);

        return new PerformanceBreakdownItem(
                name,
                money(totalStake),
                money(profit),
                roi,
                roi,
                winRate(wonCount, lostCount),
                averageOdds(performanceBets),
                drawdown(performanceBets),
                bets.size(),
                count(bets, BetStatus.PENDING),
                wonCount,
                lostCount,
                count(bets, BetStatus.VOID),
                count(bets, BetStatus.CASHOUT),
                count(bets, BetStatus.CANCELLED));
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

    private static BigDecimal winRate(long wonCount, long lostCount) {
        var resolved = wonCount + lostCount;
        if (resolved == 0) {
            return ZERO_MONEY;
        }
        return BigDecimal.valueOf(wonCount)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(resolved), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageOdds(List<AnalyticsBet> performanceBets) {
        if (performanceBets.isEmpty()) {
            return ZERO_ODDS;
        }
        var odds = performanceBets.stream()
                .map(AnalyticsBet::odds)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return odds.divide(BigDecimal.valueOf(performanceBets.size()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal drawdown(List<AnalyticsBet> performanceBets) {
        var cumulativeProfit = BigDecimal.ZERO;
        var peak = BigDecimal.ZERO;
        var maximum = BigDecimal.ZERO;
        var ordered = performanceBets.stream()
                .sorted(Comparator.comparing(AnalyticsBet::settledAt).thenComparing(AnalyticsBet::betId))
                .toList();
        for (var bet : ordered) {
            cumulativeProfit = cumulativeProfit.add(bet.profit());
            peak = peak.max(cumulativeProfit);
            maximum = maximum.max(peak.subtract(cumulativeProfit));
        }
        return money(maximum);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
