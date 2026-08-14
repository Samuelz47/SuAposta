package com.suaposta.betting.application.service;

import com.suaposta.betting.application.dto.CreateBetCommand;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.domain.model.Odds;
import com.suaposta.betting.domain.model.Stake;
import com.suaposta.betting.application.port.out.BetRepository;
import java.time.Instant;
import java.util.UUID;

public final class CreateBetService {

    private final BetRepository betRepository;

    public CreateBetService(BetRepository betRepository) {
        this.betRepository = betRepository;
    }

    public Bet create(UUID authenticatedUserId, CreateBetCommand command) {
        var now = Instant.now();
        var bet = Bet.create(
                UUID.randomUUID(),
                authenticatedUserId,
                command.sport(),
                command.league(),
                command.homeTeam(),
                command.awayTeam(),
                command.market(),
                command.selection(),
                new Odds(command.odds()),
                new Stake(command.stake()),
                command.placedAt(),
                command.notes(),
                now);
        return betRepository.save(bet);
    }
}
