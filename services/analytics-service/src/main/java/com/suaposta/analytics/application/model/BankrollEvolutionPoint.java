package com.suaposta.analytics.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BankrollEvolutionPoint(
        LocalDate date,
        BigDecimal profit,
        BigDecimal cumulativeProfit,
        BigDecimal bankroll) {
}
