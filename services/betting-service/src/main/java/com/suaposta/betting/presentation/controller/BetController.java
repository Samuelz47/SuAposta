package com.suaposta.betting.presentation.controller;

import com.suaposta.betting.application.dto.BetFilters;
import com.suaposta.betting.application.dto.BetPageRequest;
import com.suaposta.betting.application.service.CreateBetService;
import com.suaposta.betting.application.service.GetBetService;
import com.suaposta.betting.application.service.ListBetsService;
import com.suaposta.betting.application.service.SettleBetService;
import com.suaposta.betting.application.service.UpdateBetService;
import com.suaposta.betting.domain.model.BetStatus;
import com.suaposta.betting.infrastructure.config.BettingPersistenceConfiguredCondition;
import com.suaposta.betting.presentation.dto.BetPageResponse;
import com.suaposta.betting.presentation.dto.BetResponse;
import com.suaposta.betting.presentation.dto.CreateBetRequest;
import com.suaposta.betting.presentation.dto.SettleBetRequest;
import com.suaposta.betting.presentation.dto.UpdateBetRequest;
import com.suaposta.betting.presentation.exception.UnauthorizedIdentityException;
import com.suaposta.betting.presentation.mapper.BetApiMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bets")
@Conditional(BettingPersistenceConfiguredCondition.class)
public class BetController {

    private final CreateBetService createBetService;
    private final ListBetsService listBetsService;
    private final GetBetService getBetService;
    private final UpdateBetService updateBetService;
    private final SettleBetService settleBetService;

    public BetController(
            CreateBetService createBetService,
            ListBetsService listBetsService,
            GetBetService getBetService,
            UpdateBetService updateBetService,
            SettleBetService settleBetService) {
        this.createBetService = createBetService;
        this.listBetsService = listBetsService;
        this.getBetService = getBetService;
        this.updateBetService = updateBetService;
        this.settleBetService = settleBetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BetResponse create(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestBody CreateBetRequest request) {
        var bet = createBetService.create(authenticatedUserId(userIdHeader), BetApiMapper.toCommand(request));
        return BetApiMapper.toResponse(bet);
    }

    @GetMapping
    public BetPageResponse list(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "startDate", required = false) Instant startDate,
            @RequestParam(value = "endDate", required = false) Instant endDate,
            @RequestParam(value = "sport", required = false) String sport,
            @RequestParam(value = "league", required = false) String league,
            @RequestParam(value = "team", required = false) String team,
            @RequestParam(value = "market", required = false) String market,
            @RequestParam(value = "status", required = false) BetStatus status,
            @RequestParam(value = "minOdds", required = false) BigDecimal minOdds,
            @RequestParam(value = "maxOdds", required = false) BigDecimal maxOdds,
            @RequestParam(value = "minStake", required = false) BigDecimal minStake,
            @RequestParam(value = "maxStake", required = false) BigDecimal maxStake,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        var filters = new BetFilters(
                startDate,
                endDate,
                sport,
                league,
                team,
                market,
                status,
                minOdds,
                maxOdds,
                minStake,
                maxStake);
        return BetApiMapper.toPageResponse(listBetsService.list(
                authenticatedUserId(userIdHeader), filters, new BetPageRequest(page, size)));
    }

    @GetMapping("/{id}")
    public BetResponse get(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable("id") UUID betId) {
        return BetApiMapper.toResponse(getBetService.get(betId, authenticatedUserId(userIdHeader)));
    }

    @PutMapping("/{id}")
    public BetResponse update(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable("id") UUID betId,
            @RequestBody UpdateBetRequest request) {
        return BetApiMapper.toResponse(updateBetService.update(
                betId, authenticatedUserId(userIdHeader), BetApiMapper.toCommand(request)));
    }

    @PatchMapping("/{id}/settle")
    public BetResponse settle(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable("id") UUID betId,
            @RequestBody SettleBetRequest request) {
        return BetApiMapper.toResponse(settleBetService.settle(
                betId, authenticatedUserId(userIdHeader), BetApiMapper.toCommand(request)));
    }

    private static UUID authenticatedUserId(String value) {
        if (value == null) {
            throw new UnauthorizedIdentityException();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedIdentityException();
        }
    }
}
