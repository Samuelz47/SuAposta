package com.suaposta.analytics.application.model;

import java.time.Instant;

public record BankrollEvolutionFilters(
        Instant startDate,
        Instant endDate,
        String sport,
        String league,
        String team,
        String market) {
}
