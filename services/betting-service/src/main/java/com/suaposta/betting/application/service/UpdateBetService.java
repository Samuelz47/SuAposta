package com.suaposta.betting.application.service;

import com.suaposta.betting.application.dto.UpdateBetCommand;
import com.suaposta.betting.application.exception.BetNotFoundException;
import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.domain.model.Bet;
import java.time.Clock;
import java.util.UUID;

public final class UpdateBetService {

    private final BetRepository betRepository;
    private final Clock clock;

    public UpdateBetService(BetRepository betRepository, Clock clock) {
        this.betRepository = betRepository;
        this.clock = clock;
    }

    public Bet update(UUID betId, UUID authenticatedUserId, UpdateBetCommand command) {
        var bet = betRepository.findByIdAndUserId(betId, authenticatedUserId)
                .orElseThrow(BetNotFoundException::new);
        var updated = bet.update(
                command.sport(),
                command.league(),
                command.homeTeam(),
                command.awayTeam(),
                command.market(),
                command.selection(),
                command.odds(),
                command.stake(),
                command.placedAt(),
                command.notes(),
                clock.instant());
        return betRepository.save(updated);
    }
}
