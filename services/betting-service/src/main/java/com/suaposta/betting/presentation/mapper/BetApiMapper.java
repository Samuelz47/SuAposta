package com.suaposta.betting.presentation.mapper;

import com.suaposta.betting.application.dto.BetPage;
import com.suaposta.betting.application.dto.CreateBetCommand;
import com.suaposta.betting.application.dto.SettleBetCommand;
import com.suaposta.betting.application.dto.UpdateBetCommand;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.presentation.dto.BetListItemResponse;
import com.suaposta.betting.presentation.dto.BetPageResponse;
import com.suaposta.betting.presentation.dto.BetResponse;
import com.suaposta.betting.presentation.dto.CreateBetRequest;
import com.suaposta.betting.presentation.dto.SettleBetRequest;
import com.suaposta.betting.presentation.dto.UpdateBetRequest;

public final class BetApiMapper {

    private BetApiMapper() {
    }

    public static CreateBetCommand toCommand(CreateBetRequest request) {
        return new CreateBetCommand(
                request.sport(),
                request.league(),
                request.homeTeam(),
                request.awayTeam(),
                request.market(),
                request.selection(),
                request.odds(),
                request.stake(),
                request.placedAt(),
                request.notes());
    }

    public static UpdateBetCommand toCommand(UpdateBetRequest request) {
        return new UpdateBetCommand(
                request.sport(),
                request.league(),
                request.homeTeam(),
                request.awayTeam(),
                request.market(),
                request.selection(),
                request.odds(),
                request.stake(),
                request.placedAt(),
                request.notes());
    }

    public static SettleBetCommand toCommand(SettleBetRequest request) {
        return new SettleBetCommand(request.status(), request.returnAmount());
    }

    public static BetResponse toResponse(Bet bet) {
        return new BetResponse(
                bet.id(),
                bet.userId(),
                bet.sport(),
                bet.league(),
                bet.homeTeam(),
                bet.awayTeam(),
                bet.market(),
                bet.selection(),
                bet.odds().value(),
                bet.stake().value(),
                bet.status(),
                bet.profit(),
                bet.returnAmount(),
                bet.placedAt(),
                bet.settledAt(),
                bet.notes(),
                bet.createdAt(),
                bet.updatedAt());
    }

    public static BetPageResponse toPageResponse(BetPage page) {
        return new BetPageResponse(
                page.content().stream().map(BetApiMapper::toListItemResponse).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }

    private static BetListItemResponse toListItemResponse(Bet bet) {
        return new BetListItemResponse(
                bet.id(),
                bet.sport(),
                bet.league(),
                bet.homeTeam(),
                bet.awayTeam(),
                bet.market(),
                bet.selection(),
                bet.odds().value(),
                bet.stake().value(),
                bet.status(),
                bet.profit(),
                bet.returnAmount(),
                bet.placedAt(),
                bet.settledAt());
    }
}
