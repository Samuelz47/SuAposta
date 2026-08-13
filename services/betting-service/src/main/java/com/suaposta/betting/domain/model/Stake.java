package com.suaposta.betting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Stake {

    private static final int MONEY_SCALE = 2;

    private final BigDecimal value;

    public Stake(BigDecimal value) {
        BigDecimal normalizedValue = normalize(value);
        if (normalizedValue.signum() <= 0) {
            throw new IllegalArgumentException("Stake must be greater than zero");
        }
        this.value = normalizedValue;
    }

    public BigDecimal value() {
        return value;
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Stake value is required");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Stake stake)) {
            return false;
        }
        return value.equals(stake.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
