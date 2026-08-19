package com.suaposta.betting.application.service;

import com.suaposta.betting.domain.model.Bet;
import com.suaposta.messaging.contract.BetCreatedPayload;
import com.suaposta.messaging.contract.BetSettledPayload;
import com.suaposta.messaging.contract.BetUpdatedPayload;
import com.suaposta.messaging.contract.EventEnvelope;
import com.suaposta.messaging.contract.EventType;
import com.suaposta.messaging.contract.MessagingConstants;
import java.util.UUID;

final class BetEventFactory {

    private BetEventFactory() {
    }

    static EventEnvelope created(Bet bet) {
        return envelope(
                EventType.BET_CREATED,
                bet.createdAt(),
                new BetCreatedPayload(
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
                        statusOf(bet),
                        bet.placedAt()));
    }

    static EventEnvelope updated(Bet bet) {
        return envelope(
                EventType.BET_UPDATED,
                bet.updatedAt(),
                new BetUpdatedPayload(
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
                        statusOf(bet),
                        bet.placedAt(),
                        bet.updatedAt()));
    }

    static EventEnvelope settled(Bet bet) {
        return envelope(
                EventType.BET_SETTLED,
                bet.updatedAt(),
                new BetSettledPayload(
                        bet.id(),
                        bet.userId(),
                        statusOf(bet),
                        bet.odds().value(),
                        bet.stake().value(),
                        bet.profit(),
                        bet.returnAmount(),
                        bet.settledAt()));
    }

    private static EventEnvelope envelope(EventType eventType, java.time.Instant occurredAt, Object payload) {
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                occurredAt,
                MessagingConstants.VERSION_ONE,
                MessagingConstants.BETTING_SERVICE_PRODUCER,
                payload);
    }

    private static com.suaposta.messaging.contract.BetStatus statusOf(Bet bet) {
        return com.suaposta.messaging.contract.BetStatus.valueOf(bet.status().name());
    }
}
