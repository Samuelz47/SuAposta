package com.suaposta.analytics.presentation.controller;

import com.suaposta.analytics.application.model.PerformanceBreakdownFilters;
import com.suaposta.analytics.application.model.PerformanceBreakdownGroupBy;
import com.suaposta.analytics.application.service.PerformanceBreakdownService;
import com.suaposta.analytics.presentation.dto.PerformanceBreakdownResponse;
import com.suaposta.analytics.presentation.exception.UnauthorizedAnalyticsIdentityException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics/performance/breakdown")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PerformanceBreakdownController {

    private final PerformanceBreakdownService performanceBreakdownService;

    public PerformanceBreakdownController(PerformanceBreakdownService performanceBreakdownService) {
        this.performanceBreakdownService = performanceBreakdownService;
    }

    @GetMapping
    public PerformanceBreakdownResponse getPerformanceBreakdown(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "groupBy", required = false) String groupBy,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "sport", required = false) String sport,
            @RequestParam(value = "league", required = false) String league,
            @RequestParam(value = "market", required = false) String market) {
        var userId = authenticatedUserId(userIdHeader);
        var grouping = grouping(groupBy);
        var filters = filters(startDate, endDate, sport, league, market);
        var breakdown = performanceBreakdownService.getPerformanceBreakdown(userId, grouping, filters);
        return new PerformanceBreakdownResponse(breakdown.groupBy().name(), breakdown.items());
    }

    private static UUID authenticatedUserId(String value) {
        if (value == null) {
            throw new UnauthorizedAnalyticsIdentityException();
        }
        try {
            var userId = UUID.fromString(value);
            if (!userId.toString().equalsIgnoreCase(value)) {
                throw new UnauthorizedAnalyticsIdentityException();
            }
            return userId;
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedAnalyticsIdentityException();
        }
    }

    private static PerformanceBreakdownGroupBy grouping(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid performance breakdown groupBy");
        }
        try {
            return PerformanceBreakdownGroupBy.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid performance breakdown groupBy", exception);
        }
    }

    private static PerformanceBreakdownFilters filters(
            String startDate, String endDate, String sport, String league, String market) {
        var parsedStartDate = instant(startDate);
        var parsedEndDate = instant(endDate);
        validateRange(parsedStartDate, parsedEndDate);
        validateText(sport);
        validateText(league);
        validateText(market);
        return new PerformanceBreakdownFilters(parsedStartDate, parsedEndDate, sport, league, market);
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid performance breakdown filter", exception);
        }
    }

    private static void validateText(String value) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("Invalid performance breakdown filter");
        }
    }

    private static <T extends Comparable<? super T>> void validateRange(T minimum, T maximum) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Invalid performance breakdown filter");
        }
    }
}
