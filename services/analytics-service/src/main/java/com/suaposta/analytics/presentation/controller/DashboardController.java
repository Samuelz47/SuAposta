package com.suaposta.analytics.presentation.controller;

import com.suaposta.analytics.application.model.DashboardFilters;
import com.suaposta.analytics.application.service.DashboardSummaryService;
import com.suaposta.analytics.presentation.dto.DashboardResponse;
import com.suaposta.analytics.presentation.exception.UnauthorizedAnalyticsIdentityException;
import com.suaposta.messaging.contract.BetStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
@RequestMapping("/analytics/dashboard")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class DashboardController {

    private static final BigDecimal MINIMUM_ODDS = new BigDecimal("1.0000");

    private final DashboardSummaryService dashboardSummaryService;

    public DashboardController(DashboardSummaryService dashboardSummaryService) {
        this.dashboardSummaryService = dashboardSummaryService;
    }

    @GetMapping
    public DashboardResponse getDashboard(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "sport", required = false) String sport,
            @RequestParam(value = "league", required = false) String league,
            @RequestParam(value = "team", required = false) String team,
            @RequestParam(value = "market", required = false) String market,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "minOdds", required = false) String minOdds,
            @RequestParam(value = "maxOdds", required = false) String maxOdds,
            @RequestParam(value = "minStake", required = false) String minStake,
            @RequestParam(value = "maxStake", required = false) String maxStake) {
        var filters = filters(
                startDate, endDate, sport, league, team, market, status, minOdds, maxOdds, minStake, maxStake);
        return new DashboardResponse(
                dashboardSummaryService.getDashboard(authenticatedUserId(userIdHeader), filters), filters);
    }

    private static DashboardFilters filters(
            String startDate,
            String endDate,
            String sport,
            String league,
            String team,
            String market,
            String status,
            String minOdds,
            String maxOdds,
            String minStake,
            String maxStake) {
        var parsedStartDate = instant(startDate);
        var parsedEndDate = instant(endDate);
        var parsedMinOdds = decimal(minOdds, 4);
        var parsedMaxOdds = decimal(maxOdds, 4);
        var parsedMinStake = decimal(minStake, 2);
        var parsedMaxStake = decimal(maxStake, 2);
        validateRange(parsedStartDate, parsedEndDate);
        validateText(sport);
        validateText(league);
        validateText(team);
        validateText(market);
        validateOdds(parsedMinOdds);
        validateOdds(parsedMaxOdds);
        validateRange(parsedMinOdds, parsedMaxOdds);
        validateStake(parsedMinStake);
        validateStake(parsedMaxStake);
        validateRange(parsedMinStake, parsedMaxStake);
        return new DashboardFilters(
                parsedStartDate, parsedEndDate, sport, league, team, market, status(status),
                parsedMinOdds, parsedMaxOdds, parsedMinStake, parsedMaxStake);
    }

    private static UUID authenticatedUserId(String value) {
        if (value == null) {
            throw new UnauthorizedAnalyticsIdentityException();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedAnalyticsIdentityException();
        }
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid dashboard filter", exception);
        }
    }

    private static BigDecimal decimal(String value, int scale) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid dashboard filter", exception);
        }
    }

    private static BetStatus status(String value) {
        if (value == null) {
            return null;
        }
        try {
            return BetStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid dashboard filter", exception);
        }
    }

    private static void validateText(String value) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("Invalid dashboard filter");
        }
    }

    private static void validateOdds(BigDecimal value) {
        if (value != null && value.compareTo(MINIMUM_ODDS) <= 0) {
            throw new IllegalArgumentException("Invalid dashboard filter");
        }
    }

    private static void validateStake(BigDecimal value) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException("Invalid dashboard filter");
        }
    }

    private static <T extends Comparable<? super T>> void validateRange(T minimum, T maximum) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Invalid dashboard filter");
        }
    }
}
