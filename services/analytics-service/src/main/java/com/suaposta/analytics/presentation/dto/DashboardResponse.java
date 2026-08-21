package com.suaposta.analytics.presentation.dto;

import com.suaposta.analytics.application.model.DashboardFilters;
import com.suaposta.analytics.application.model.DashboardSummary;

public record DashboardResponse(DashboardSummary summary, DashboardFilters filters) {
}
