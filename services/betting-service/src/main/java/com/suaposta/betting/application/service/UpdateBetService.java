package com.suaposta.betting.application.service;

import com.suaposta.betting.application.dto.UpdateBetCommand;
import com.suaposta.betting.application.exception.BetNotFoundException;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.messaging.contract.MessagingConstants;
import java.time.Clock;
import java.util.UUID;

public final class UpdateBetService {

    private final BetRepository betRepository;
    private final Clock clock;
    private final BetEventPublisher eventPublisher;

    public UpdateBetService(BetRepository betRepository, Clock clock, BetEventPublisher eventPublisher) {
        this.betRepository = betRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
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
        var persisted = betRepository.save(updated);
        eventPublisher.publish(
                BetEventFactory.updated(persisted),
                MessagingConstants.BET_UPDATED_ROUTING_KEY);
        return persisted;
    }
}
