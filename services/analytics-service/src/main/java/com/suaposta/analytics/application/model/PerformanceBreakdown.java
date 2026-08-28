package com.suaposta.analytics.application.model;

import java.util.List;

public record PerformanceBreakdown(
        PerformanceBreakdownGroupBy groupBy,
        List<PerformanceBreakdownItem> items) {
}
