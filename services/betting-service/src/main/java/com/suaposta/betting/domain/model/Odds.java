package com.suaposta.betting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Odds {

    private static final int ODDS_SCALE = 4;

    private final BigDecimal value;

    public Odds(BigDecimal value) {
        BigDecimal normalizedValue = normalize(value);
        if (normalizedValue.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("Odds must be greater than one");
        }
        this.value = normalizedValue;
    }

    public BigDecimal value() {
        return value;
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Odds value is required");
        }
        return value.setScale(ODDS_SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Odds odds)) {
            return false;
        }
        return value.equals(odds.value);
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
