package com.suaposta.betting.application.service;

import com.suaposta.betting.application.exception.BetNotFoundException;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.application.port.out.BetRepository;
import java.util.UUID;

public final class GetBetService {

    private final BetRepository betRepository;

    public GetBetService(BetRepository betRepository) {
        this.betRepository = betRepository;
    }

    public Bet get(UUID betId, UUID authenticatedUserId) {
        return betRepository.findByIdAndUserId(betId, authenticatedUserId)
                .orElseThrow(BetNotFoundException::new);
    }
}
