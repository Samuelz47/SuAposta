package com.suaposta.analytics.presentation.controller;

import com.suaposta.analytics.application.model.BankrollEvolutionFilters;
import com.suaposta.analytics.application.service.BankrollEvolutionService;
import com.suaposta.analytics.presentation.dto.BankrollEvolutionResponse;
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
@RequestMapping("/analytics/bankroll-evolution")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class BankrollEvolutionController {

    private final BankrollEvolutionService bankrollEvolutionService;

    public BankrollEvolutionController(BankrollEvolutionService bankrollEvolutionService) {
        this.bankrollEvolutionService = bankrollEvolutionService;
    }

    @GetMapping
    public BankrollEvolutionResponse getBankrollEvolution(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "sport", required = false) String sport,
            @RequestParam(value = "league", required = false) String league,
            @RequestParam(value = "team", required = false) String team,
            @RequestParam(value = "market", required = false) String market) {
        var userId = authenticatedUserId(userIdHeader);
        var filters = filters(startDate, endDate, sport, league, team, market);
        var evolution = bankrollEvolutionService.getBankrollEvolution(userId, filters);
        return new BankrollEvolutionResponse(evolution.points());
    }

    private static BankrollEvolutionFilters filters(
            String startDate, String endDate, String sport, String league, String team, String market) {
        var parsedStartDate = instant(startDate);
        var parsedEndDate = instant(endDate);
        validateRange(parsedStartDate, parsedEndDate);
        validateText(sport);
        validateText(league);
        validateText(team);
        validateText(market);
        return new BankrollEvolutionFilters(parsedStartDate, parsedEndDate, sport, league, team, market);
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
            throw new IllegalArgumentException("Invalid bankroll evolution filter", exception);
        }
    }

    private static void validateText(String value) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("Invalid bankroll evolution filter");
        }
    }

    private static <T extends Comparable<? super T>> void validateRange(T minimum, T maximum) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Invalid bankroll evolution filter");
        }
    }
}
