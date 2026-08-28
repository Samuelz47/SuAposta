package com.suaposta.analytics.application.model;

import java.time.Instant;

public record PerformanceBreakdownFilters(
        Instant startDate,
        Instant endDate,
        String sport,
        String league,
        String market) {
}
