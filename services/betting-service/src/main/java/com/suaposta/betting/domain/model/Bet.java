package com.suaposta.betting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public final class Bet {

    private static final int MONEY_SCALE = 2;

    private final Stake stake;
    private final Odds odds;

    private BetStatus status;
    private BigDecimal profit;
    private BigDecimal returnAmount;
    private Instant settledAt;

    public Bet(Stake stake, Odds odds) {
        if (stake == null) {
            throw new IllegalArgumentException("Stake is required");
        }
        if (odds == null) {
            throw new IllegalArgumentException("Odds are required");
        }
        this.stake = stake;
        this.odds = odds;
        this.status = BetStatus.PENDING;
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

    public void settle(BetStatus targetStatus) {
        settle(targetStatus, null);
    }

    public void settle(BetStatus targetStatus, BigDecimal cashoutReturn) {
        ensurePending();
        if (targetStatus == null) {
            throw new IllegalArgumentException("Settlement status is required");
        }

        Settlement settlement = calculateSettlement(targetStatus, cashoutReturn);

        status = targetStatus;
        profit = settlement.profit();
        returnAmount = settlement.returnAmount();
    }

    private void ensurePending() {
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
