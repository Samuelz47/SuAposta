package com.suaposta.betting.application.service;

import com.suaposta.betting.application.dto.CreateBetCommand;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.domain.model.Odds;
import com.suaposta.betting.domain.model.Stake;
import com.suaposta.messaging.contract.MessagingConstants;
import java.time.Instant;
import java.util.UUID;

public final class CreateBetService {

    private final BetRepository betRepository;
    private final BetEventPublisher eventPublisher;

    public CreateBetService(BetRepository betRepository, BetEventPublisher eventPublisher) {
        this.betRepository = betRepository;
        this.eventPublisher = eventPublisher;
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
        var persisted = betRepository.save(bet);
        eventPublisher.publish(
                BetEventFactory.created(persisted),
                MessagingConstants.BET_CREATED_ROUTING_KEY);
        return persisted;
    }
}
