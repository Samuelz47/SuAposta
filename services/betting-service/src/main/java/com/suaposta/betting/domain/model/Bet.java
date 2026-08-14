package com.suaposta.betting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public final class Bet {

    private static final int MONEY_SCALE = 2;

    private final UUID id;
    private final UUID userId;
    private final String sport;
    private final String league;
    private final String homeTeam;
    private final String awayTeam;
    private final String market;
    private final String selection;
    private final Stake stake;
    private final Odds odds;
    private final Instant placedAt;
    private final String notes;
    private final Instant createdAt;
    private Instant updatedAt;

    private BetStatus status;
    private BigDecimal profit;
    private BigDecimal returnAmount;
    private Instant settledAt;

    public Bet(Stake stake, Odds odds) {
        this(null, null, null, null, null, null, null, null, odds, stake,
                BetStatus.PENDING, null, null, null, null, null, null, null);
    }

    private Bet(
            UUID id,
            UUID userId,
            String sport,
            String league,
            String homeTeam,
            String awayTeam,
            String market,
            String selection,
            Odds odds,
            Stake stake,
            BetStatus status,
            BigDecimal profit,
            BigDecimal returnAmount,
            Instant placedAt,
            Instant settledAt,
            String notes,
            Instant createdAt,
            Instant updatedAt) {
        if (stake == null) {
            throw new IllegalArgumentException("Stake is required");
        }
        if (odds == null) {
            throw new IllegalArgumentException("Odds are required");
        }
        this.id = id;
        this.userId = userId;
        this.sport = sport;
        this.league = league;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.market = market;
        this.selection = selection;
        this.odds = odds;
        this.stake = stake;
        this.status = status;
        this.profit = profit;
        this.returnAmount = returnAmount;
        this.placedAt = placedAt;
        this.settledAt = settledAt;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Bet create(
            UUID id,
            UUID userId,
            String sport,
            String league,
            String homeTeam,
            String awayTeam,
            String market,
            String selection,
            Odds odds,
            Stake stake,
            Instant placedAt,
            String notes,
            Instant createdAt) {
        return new Bet(
                id, userId, sport, league, homeTeam, awayTeam, market, selection, odds, stake,
                BetStatus.PENDING, null, null, placedAt, null, notes, createdAt, createdAt);
    }

    public static Bet restore(
            UUID id,
            UUID userId,
            String sport,
            String league,
            String homeTeam,
            String awayTeam,
            String market,
            String selection,
            Odds odds,
            Stake stake,
            BetStatus status,
            BigDecimal profit,
            BigDecimal returnAmount,
            Instant placedAt,
            Instant settledAt,
            String notes,
            Instant createdAt,
            Instant updatedAt) {
        return new Bet(
                id, userId, sport, league, homeTeam, awayTeam, market, selection, odds, stake,
                status, profit, returnAmount, placedAt, settledAt, notes, createdAt, updatedAt);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String sport() {
        return sport;
    }

    public String league() {
        return league;
    }

    public String homeTeam() {
        return homeTeam;
    }

    public String awayTeam() {
        return awayTeam;
    }

    public String market() {
        return market;
    }

    public String selection() {
        return selection;
    }

    public Stake stake() {
        return stake;
    }

    public Odds odds() {
        return odds;
    }

    public BetStatus status() {
        return status;
    }

    public BigDecimal profit() {
        return profit;
    }

    public BigDecimal returnAmount() {
        return returnAmount;
    }

    public Instant settledAt() {
        return settledAt;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public String notes() {
        return notes;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Bet update(
            String sport,
            String league,
            String homeTeam,
            String awayTeam,
            String market,
            String selection,
            BigDecimal odds,
            BigDecimal stake,
            Instant placedAt,
            String notes,
            Instant updatedAt) {
        ensurePendingForUpdate();

        var normalizedOdds = new Odds(odds);
        var normalizedStake = new Stake(stake);

        return new Bet(
                id,
                userId,
                sport,
                league,
                homeTeam,
                awayTeam,
                market,
                selection,
                normalizedOdds,
                normalizedStake,
                BetStatus.PENDING,
                null,
                null,
                placedAt,
                null,
                notes,
                createdAt,
                updatedAt);
    }

    public void settle(BetStatus targetStatus) {
        settle(targetStatus, null);
    }

    public void settle(BetStatus targetStatus, BigDecimal cashoutReturn) {
        ensurePendingForSettlement();
        if (targetStatus == null) {
            throw new IllegalArgumentException("Settlement status is required");
        }

        Settlement settlement = calculateSettlement(targetStatus, cashoutReturn);

        status = targetStatus;
        profit = settlement.profit();
        returnAmount = settlement.returnAmount();
    }

    public void settleAt(BetStatus targetStatus, BigDecimal cashoutReturn, Instant operationTime) {
        ensurePendingForSettlement();
        if (targetStatus == null) {
            throw new IllegalArgumentException("Settlement status is required");
        }
        if (operationTime == null) {
            throw new IllegalArgumentException("Settlement time is required");
        }

        Settlement settlement = calculateSettlement(targetStatus, cashoutReturn);

        status = targetStatus;
        profit = settlement.profit();
        returnAmount = settlement.returnAmount();
        settledAt = operationTime;
        updatedAt = operationTime;
    }

    private void ensurePendingForUpdate() {
        if (status != BetStatus.PENDING) {
            throw new IllegalStateException("Only pending bets can be updated");
        }
    }

    private void ensurePendingForSettlement() {
        if (status != BetStatus.PENDING) {
            throw new IllegalStateException("Only pending bets can be settled");
        }
    }

    private Settlement calculateSettlement(BetStatus targetStatus, BigDecimal cashoutReturn) {
        return switch (targetStatus) {
            case WON -> calculateWonSettlement();
            case LOST -> new Settlement(normalizeMoney(stake.value().negate()), zeroMoney());
            case VOID, CANCELLED -> new Settlement(zeroMoney(), normalizeMoney(stake.value()));
            case CASHOUT -> calculateCashoutSettlement(cashoutReturn);
            case PENDING -> throw new IllegalArgumentException("A pending bet cannot settle as pending");
        };
    }

    private Settlement calculateWonSettlement() {
        BigDecimal calculatedReturnAmount = normalizeMoney(stake.value().multiply(odds.value()));
        BigDecimal calculatedProfit = normalizeMoney(calculatedReturnAmount.subtract(stake.value()));
        return new Settlement(calculatedProfit, calculatedReturnAmount);
    }

    private Settlement calculateCashoutSettlement(BigDecimal cashoutReturn) {
        if (cashoutReturn == null) {
            throw new IllegalArgumentException("Cashout return amount is required");
        }
        BigDecimal normalizedReturnAmount = normalizeMoney(cashoutReturn);
        BigDecimal calculatedProfit = normalizeMoney(normalizedReturnAmount.subtract(stake.value()));
        return new Settlement(calculatedProfit, normalizedReturnAmount);
    }

    private static BigDecimal zeroMoney() {
        return normalizeMoney(BigDecimal.ZERO);
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private record Settlement(BigDecimal profit, BigDecimal returnAmount) {
    }
}
