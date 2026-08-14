package com.suaposta.betting.application.service;

import com.suaposta.betting.application.dto.SettleBetCommand;
import com.suaposta.betting.application.exception.BetNotFoundException;
import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.domain.model.Bet;
import java.time.Clock;
import java.util.UUID;

public final class SettleBetService {

    private final BetRepository betRepository;
    private final Clock clock;

    public SettleBetService(BetRepository betRepository, Clock clock) {
        this.betRepository = betRepository;
        this.clock = clock;
    }

    public Bet settle(UUID betId, UUID authenticatedUserId, SettleBetCommand command) {
        var bet = betRepository.findByIdAndUserId(betId, authenticatedUserId)
                .orElseThrow(BetNotFoundException::new);
        var operationTime = clock.instant();
        bet.settleAt(command.status(), command.returnAmount(), operationTime);
        return betRepository.save(bet);
    }
}
