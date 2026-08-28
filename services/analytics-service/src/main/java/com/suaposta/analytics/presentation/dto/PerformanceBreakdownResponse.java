package com.suaposta.analytics.presentation.dto;

import com.suaposta.analytics.application.model.PerformanceBreakdownItem;
import java.util.List;

public record PerformanceBreakdownResponse(
        String groupBy,
        List<PerformanceBreakdownItem> items) {
}
