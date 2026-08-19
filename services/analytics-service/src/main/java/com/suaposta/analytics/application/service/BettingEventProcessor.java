package com.suaposta.analytics.application.service;

import com.suaposta.analytics.application.model.AnalyticsBet;
import com.suaposta.analytics.application.model.ProcessedEvent;
import com.suaposta.analytics.application.port.out.AnalyticsBetRepository;
import com.suaposta.analytics.application.port.out.ProcessedEventRepository;
import com.suaposta.messaging.contract.BetCreatedPayload;
import com.suaposta.messaging.contract.BetSettledPayload;
import com.suaposta.messaging.contract.BetStatus;
import com.suaposta.messaging.contract.BetUpdatedPayload;
import com.suaposta.messaging.contract.EventEnvelope;
import com.suaposta.messaging.contract.EventType;
import com.suaposta.messaging.contract.MessagingConstants;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

public class BettingEventProcessor {

    private final AnalyticsBetRepository analyticsBetRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final Clock clock;

    public BettingEventProcessor(
            AnalyticsBetRepository analyticsBetRepository,
            ProcessedEventRepository processedEventRepository,
            Clock clock) {
        this.analyticsBetRepository = analyticsBetRepository;
        this.processedEventRepository = processedEventRepository;
        this.clock = clock;
    }

    @Transactional
    public void process(EventEnvelope envelope) {
        validateEnvelope(envelope);
        validatePayload(envelope);

        if (processedEventRepository.findByEventId(envelope.eventId()).isPresent()) {
            return;
        }

        var processedEvent = new ProcessedEvent(envelope.eventId(), envelope.eventType(), clock.instant());
        if (processedEventRepository.claim(processedEvent) == null) {
            return;
        }

        switch (envelope.eventType()) {
            case BET_CREATED -> createProjection(envelope, (BetCreatedPayload) envelope.payload());
            case BET_UPDATED -> updateProjection((BetUpdatedPayload) envelope.payload());
            case BET_SETTLED -> settleProjection(envelope, (BetSettledPayload) envelope.payload());
        }
    }

    private void createProjection(EventEnvelope envelope, BetCreatedPayload payload) {
        if (analyticsBetRepository.findByBetId(payload.betId()).isPresent()) {
            throw new IllegalStateException("Analytics projection already exists for betId " + payload.betId());
        }
        analyticsBetRepository.insert(new AnalyticsBet(
                payload.betId(),
                payload.betId(),
                payload.userId(),
                payload.sport(),
                payload.league(),
                payload.homeTeam(),
                payload.awayTeam(),
                payload.market(),
                payload.selection(),
                payload.odds(),
                payload.stake(),
                payload.status(),
                null,
                null,
                payload.placedAt(),
                null,
                envelope.occurredAt(),
                envelope.occurredAt()));
    }

    private void updateProjection(BetUpdatedPayload payload) {
        var current = requiredProjection(payload.betId());
        requireMatchingUser(current, payload.userId());
        analyticsBetRepository.update(new AnalyticsBet(
                current.id(),
                current.betId(),
                current.userId(),
                payload.sport(),
                payload.league(),
                payload.homeTeam(),
                payload.awayTeam(),
                payload.market(),
                payload.selection(),
                payload.odds(),
                payload.stake(),
                current.status(),
                current.profit(),
                current.returnAmount(),
                payload.placedAt(),
                current.settledAt(),
                current.createdAt(),
                payload.updatedAt()));
    }

    private void settleProjection(EventEnvelope envelope, BetSettledPayload payload) {
        var current = requiredProjection(payload.betId());
        requireMatchingUser(current, payload.userId());
        analyticsBetRepository.update(new AnalyticsBet(
                current.id(),
                current.betId(),
                current.userId(),
                current.sport(),
                current.league(),
                current.homeTeam(),
                current.awayTeam(),
                current.market(),
                current.selection(),
                payload.odds(),
                payload.stake(),
                payload.status(),
                payload.profit(),
                payload.returnAmount(),
                current.placedAt(),
                payload.settledAt(),
                current.createdAt(),
                envelope.occurredAt()));
    }

    private AnalyticsBet requiredProjection(java.util.UUID betId) {
        return analyticsBetRepository.findByBetId(betId)
                .orElseThrow(() -> new IllegalStateException("Analytics projection not found for betId " + betId));
    }

    private static void requireMatchingUser(AnalyticsBet current, java.util.UUID eventUserId) {
        if (!current.userId().equals(eventUserId)) {
            throw new IllegalStateException("Analytics projection ownership does not match event ownership");
        }
    }

    private static void validateEnvelope(EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(envelope.eventId(), "eventId must not be null");
        Objects.requireNonNull(envelope.eventType(), "eventType must not be null");
        Objects.requireNonNull(envelope.occurredAt(), "occurredAt must not be null");
        Objects.requireNonNull(envelope.payload(), "payload must not be null");
        if (envelope.version() != MessagingConstants.VERSION_ONE) {
            throw new IllegalArgumentException("Unsupported event version " + envelope.version());
        }
        if (!MessagingConstants.BETTING_SERVICE_PRODUCER.equals(envelope.producer())) {
            throw new IllegalArgumentException("Unsupported event producer " + envelope.producer());
        }
    }

    private static void validatePayload(EventEnvelope envelope) {
        switch (envelope.eventType()) {
            case BET_CREATED -> validateCreated(requirePayload(envelope, BetCreatedPayload.class));
            case BET_UPDATED -> validateUpdated(requirePayload(envelope, BetUpdatedPayload.class));
            case BET_SETTLED -> validateSettled(requirePayload(envelope, BetSettledPayload.class));
        }
    }

    private static <T> T requirePayload(EventEnvelope envelope, Class<T> payloadType) {
        if (!payloadType.isInstance(envelope.payload())) {
            throw new IllegalArgumentException(
                    envelope.eventType() + " requires payload " + payloadType.getSimpleName());
        }
        return payloadType.cast(envelope.payload());
    }

    private static void validateCreated(BetCreatedPayload payload) {
        requireCommonProjectionFields(
                payload.betId(), payload.userId(), payload.sport(), payload.league(), payload.homeTeam(),
                payload.awayTeam(), payload.market(), payload.selection(), payload.odds(), payload.stake(),
                payload.status(), payload.placedAt());
        if (payload.status() != BetStatus.PENDING) {
            throw new IllegalArgumentException("BET_CREATED status must be PENDING");
        }
    }

    private static void validateUpdated(BetUpdatedPayload payload) {
        requireCommonProjectionFields(
                payload.betId(), payload.userId(), payload.sport(), payload.league(), payload.homeTeam(),
                payload.awayTeam(), payload.market(), payload.selection(), payload.odds(), payload.stake(),
                payload.status(), payload.placedAt());
        Objects.requireNonNull(payload.updatedAt(), "updatedAt must not be null");
        if (payload.status() != BetStatus.PENDING) {
            throw new IllegalArgumentException("BET_UPDATED status must be PENDING");
        }
    }

    private static void validateSettled(BetSettledPayload payload) {
        Objects.requireNonNull(payload.betId(), "betId must not be null");
        Objects.requireNonNull(payload.userId(), "userId must not be null");
        Objects.requireNonNull(payload.status(), "status must not be null");
        Objects.requireNonNull(payload.odds(), "odds must not be null");
        Objects.requireNonNull(payload.stake(), "stake must not be null");
        Objects.requireNonNull(payload.profit(), "profit must not be null");
        Objects.requireNonNull(payload.returnAmount(), "returnAmount must not be null");
        Objects.requireNonNull(payload.settledAt(), "settledAt must not be null");
        if (payload.status() == BetStatus.PENDING) {
            throw new IllegalArgumentException("BET_SETTLED status must be final");
        }
    }

    private static void requireCommonProjectionFields(Object... fields) {
        for (var field : fields) {
            Objects.requireNonNull(field, "projection payload field must not be null");
        }
    }
}
