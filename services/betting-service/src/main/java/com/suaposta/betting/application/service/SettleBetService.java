package com.suaposta.betting.application.service;

import com.suaposta.betting.application.dto.SettleBetCommand;
import com.suaposta.betting.application.exception.BetNotFoundException;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.messaging.contract.MessagingConstants;
import java.time.Clock;
import java.util.UUID;

public final class SettleBetService {

    private final BetRepository betRepository;
    private final Clock clock;
    private final BetEventPublisher eventPublisher;

    public SettleBetService(BetRepository betRepository, Clock clock, BetEventPublisher eventPublisher) {
        this.betRepository = betRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    public Bet settle(UUID betId, UUID authenticatedUserId, SettleBetCommand command) {
        var bet = betRepository.findByIdAndUserId(betId, authenticatedUserId)
                .orElseThrow(BetNotFoundException::new);
        var operationTime = clock.instant();
        bet.settleAt(command.status(), command.returnAmount(), operationTime);
        var persisted = betRepository.save(bet);
        eventPublisher.publish(
                BetEventFactory.settled(persisted),
                MessagingConstants.BET_SETTLED_ROUTING_KEY);
        return persisted;
    }
}
