package com.suaposta.betting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.suaposta.betting.application.dto.CreateBetCommand;
import com.suaposta.betting.application.dto.SettleBetCommand;
import com.suaposta.betting.application.dto.UpdateBetCommand;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.domain.model.BetStatus;
import com.suaposta.betting.domain.model.Odds;
import com.suaposta.betting.domain.model.Stake;
import com.suaposta.messaging.contract.BetCreatedPayload;
import com.suaposta.messaging.contract.BetSettledPayload;
import com.suaposta.messaging.contract.BetUpdatedPayload;
import com.suaposta.messaging.contract.EventEnvelope;
import com.suaposta.messaging.contract.EventType;
import com.suaposta.messaging.contract.MessagingConstants;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class Task62ApplicationPublishingTest {

    private static final UUID AUTHENTICATED_USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PERSISTED_USER_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID BET_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant INPUT_PLACED_AT = Instant.parse("2026-07-21T20:30:00Z");
    private static final Instant ORIGINAL_CREATED_AT = Instant.parse("2026-07-21T21:00:00Z");
    private static final Instant PERSISTED_CREATED_AT = Instant.parse("2026-07-21T21:05:00Z");
    private static final Instant PERSISTED_UPDATED_AT = Instant.parse("2026-07-21T21:20:00Z");
    private static final Instant PERSISTED_SETTLED_AT = Instant.parse("2026-07-21T22:00:00Z");
    private static final Clock CLOCK = Clock.fixed(PERSISTED_UPDATED_AT, ZoneOffset.UTC);

    @Mock
    private BetRepository betRepository;

    @Mock
    private BetEventPublisher publisher;

    private CreateBetService createBetService;
    private UpdateBetService updateBetService;
    private SettleBetService settleBetService;

    @BeforeEach
    void setUp() {
        createBetService = new CreateBetService(betRepository, publisher);
        updateBetService = new UpdateBetService(betRepository, CLOCK, publisher);
        settleBetService = new SettleBetService(betRepository, CLOCK, publisher);
    }

    @Test
    void should_persist_before_publishing_one_created_event_from_the_returned_bet() {
        var persisted = persistedCreatedBet();
        when(betRepository.save(any(Bet.class))).thenReturn(persisted);

        var result = createBetService.create(AUTHENTICATED_USER_ID, createCommand());

        var envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        var routingKey = ArgumentCaptor.forClass(String.class);
        var order = inOrder(betRepository, publisher);
        order.verify(betRepository).save(any(Bet.class));
        order.verify(publisher).publish(envelope.capture(), routingKey.capture());
        order.verifyNoMoreInteractions();
        verifyNoMoreInteractions(betRepository, publisher);

        assertThat(result).isSameAs(persisted);
        assertCreatedEvent(envelope.getValue(), routingKey.getValue(), persisted);
    }

    @Test
    void should_propagate_publication_failure_after_creation_persistence_without_retrying() {
        var persisted = persistedCreatedBet();
        when(betRepository.save(any(Bet.class))).thenReturn(persisted);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).publish(any(EventEnvelope.class), anyString());

        assertThatThrownBy(() -> createBetService.create(AUTHENTICATED_USER_ID, createCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broker unavailable");

        verify(betRepository).save(any(Bet.class));
        verify(publisher).publish(any(EventEnvelope.class),
                org.mockito.ArgumentMatchers.eq(MessagingConstants.BET_CREATED_ROUTING_KEY));
        verifyNoMoreInteractions(betRepository, publisher);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCreationInputs")
    void should_not_publish_when_creation_validation_fails(
            String scenario, BigDecimal stake, BigDecimal odds) {
        var command = new CreateBetCommand(
                "FOOTBALL", "League", "Home", "Away", "MATCH_RESULT", "Home",
                odds, stake, INPUT_PLACED_AT, "input values");

        assertThatThrownBy(() -> createBetService.create(AUTHENTICATED_USER_ID, command))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(betRepository, publisher);
    }

    @Test
    void should_not_publish_when_creation_persistence_fails() {
        when(betRepository.save(any(Bet.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> createBetService.create(AUTHENTICATED_USER_ID, createCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(betRepository).save(any(Bet.class));
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    @Test
    void should_persist_before_publishing_one_updated_event_from_the_returned_bet() {
        var original = pendingBet(AUTHENTICATED_USER_ID);
        var persisted = persistedUpdatedBet();
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(original));
        when(betRepository.save(any(Bet.class))).thenReturn(persisted);

        var result = updateBetService.update(BET_ID, AUTHENTICATED_USER_ID, updateCommand());

        var envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        var routingKey = ArgumentCaptor.forClass(String.class);
        var order = inOrder(betRepository, publisher);
        order.verify(betRepository).findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID);
        order.verify(betRepository).save(any(Bet.class));
        order.verify(publisher).publish(envelope.capture(), routingKey.capture());
        order.verifyNoMoreInteractions();
        verifyNoMoreInteractions(betRepository, publisher);

        assertThat(result).isSameAs(persisted);
        assertUpdatedEvent(envelope.getValue(), routingKey.getValue(), persisted);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingOrCrossUserLookupCases")
    void should_not_publish_when_update_lookup_is_missing_or_cross_user(
            String scenario, UUID requestedBetId) {
        when(betRepository.findByIdAndUserId(requestedBetId, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateBetService.update(
                requestedBetId, AUTHENTICATED_USER_ID, updateCommand()))
                .isInstanceOf(RuntimeException.class);

        verify(betRepository).findByIdAndUserId(requestedBetId, AUTHENTICATED_USER_ID);
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    @Test
    void should_not_publish_when_update_targets_a_final_bet() {
        var finalBet = finalBet(AUTHENTICATED_USER_ID, BetStatus.WON);
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(finalBet));

        assertThatThrownBy(() -> updateBetService.update(BET_ID, AUTHENTICATED_USER_ID, updateCommand()))
                .isInstanceOf(RuntimeException.class);

        verify(betRepository).findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID);
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUpdateInputs")
    void should_not_publish_when_update_validation_fails(
            String scenario, BigDecimal stake, BigDecimal odds) {
        var original = pendingBet(AUTHENTICATED_USER_ID);
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(original));
        var command = new UpdateBetCommand(
                "TENNIS", "Updated League", "Player A", "Player B", "MATCH_WINNER", "Player A",
                odds, stake, INPUT_PLACED_AT, "updated input values");

        assertThatThrownBy(() -> updateBetService.update(BET_ID, AUTHENTICATED_USER_ID, command))
                .isInstanceOf(RuntimeException.class);

        verify(betRepository).findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID);
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    @Test
    void should_not_publish_when_update_persistence_fails() {
        var original = pendingBet(AUTHENTICATED_USER_ID);
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(original));
        when(betRepository.save(any(Bet.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> updateBetService.update(BET_ID, AUTHENTICATED_USER_ID, updateCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(betRepository).findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID);
        verify(betRepository).save(any(Bet.class));
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("successfulSettlements")
    void should_persist_before_publishing_one_settled_event_from_the_returned_bet(
            String scenario,
            BetStatus status,
            BigDecimal persistedOdds,
            BigDecimal persistedStake,
            BigDecimal persistedProfit,
            BigDecimal persistedReturnAmount,
            BigDecimal commandCashoutReturn) {
        var original = pendingBet(AUTHENTICATED_USER_ID);
        var persisted = persistedSettledBet(
                status, persistedOdds, persistedStake, persistedProfit, persistedReturnAmount);
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(original));
        when(betRepository.save(any(Bet.class))).thenReturn(persisted);

        var result = settleBetService.settle(
                BET_ID,
                AUTHENTICATED_USER_ID,
                new SettleBetCommand(status, commandCashoutReturn));

        var envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        var routingKey = ArgumentCaptor.forClass(String.class);
        var order = inOrder(betRepository, publisher);
        order.verify(betRepository).findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID);
        order.verify(betRepository).save(any(Bet.class));
        order.verify(publisher).publish(envelope.capture(), routingKey.capture());
        order.verifyNoMoreInteractions();
        verifyNoMoreInteractions(betRepository, publisher);

        assertThat(result).isSameAs(persisted);
        assertSettledEvent(envelope.getValue(), routingKey.getValue(), persisted);
    }

    @Test
    void should_generate_distinct_event_ids_for_created_updated_and_settled_lifecycle_events() {
        var pending = pendingBet(AUTHENTICATED_USER_ID);
        var created = persistedCreatedBetWithId(BET_ID);
        var updated = persistedUpdatedBet();
        var settled = persistedSettledBet(
                BetStatus.WON,
                new BigDecimal("3.3333"),
                new BigDecimal("77.77"),
                new BigDecimal("181.45"),
                new BigDecimal("259.22"));
        when(betRepository.save(any(Bet.class))).thenReturn(created, updated, settled);
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(pending), Optional.of(pending));

        createBetService.create(AUTHENTICATED_USER_ID, createCommand());
        updateBetService.update(BET_ID, AUTHENTICATED_USER_ID, updateCommand());
        settleBetService.settle(
                BET_ID, AUTHENTICATED_USER_ID, new SettleBetCommand(BetStatus.WON, null));

        var envelopes = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(publisher, org.mockito.Mockito.times(3))
                .publish(envelopes.capture(), anyString());
        var eventIds = envelopes.getAllValues().stream().map(EventEnvelope::eventId).toList();
        assertThat(eventIds).doesNotHaveDuplicates();
        assertThat(eventIds).allSatisfy(eventId -> assertThat(eventId).isNotEqualTo(BET_ID));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("finalStatuses")
    void should_not_publish_when_settlement_targets_an_already_final_bet(
            String scenario, BetStatus existingStatus) {
        var finalBet = finalBet(AUTHENTICATED_USER_ID, existingStatus);
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(finalBet));

        assertThatThrownBy(() -> settleBetService.settle(
                BET_ID, AUTHENTICATED_USER_ID, new SettleBetCommand(existingStatus, null)))
                .isInstanceOf(RuntimeException.class);

        verify(betRepository).findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID);
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    @Test
    void should_not_publish_when_pending_to_pending_settlement_is_requested() {
        var pending = pendingBet(AUTHENTICATED_USER_ID);
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> settleBetService.settle(
                BET_ID, AUTHENTICATED_USER_ID, new SettleBetCommand(BetStatus.PENDING, null)))
                .isInstanceOf(RuntimeException.class);

        verify(betRepository).findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID);
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    @Test
    void should_not_publish_when_cashout_return_amount_is_missing() {
        var pending = pendingBet(AUTHENTICATED_USER_ID);
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> settleBetService.settle(
                BET_ID, AUTHENTICATED_USER_ID, new SettleBetCommand(BetStatus.CASHOUT, null)))
                .isInstanceOf(RuntimeException.class);

        verify(betRepository).findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID);
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingOrCrossUserLookupCases")
    void should_not_publish_when_settlement_lookup_is_missing_or_cross_user(
            String scenario, UUID requestedBetId) {
        when(betRepository.findByIdAndUserId(requestedBetId, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> settleBetService.settle(
                requestedBetId,
                AUTHENTICATED_USER_ID,
                new SettleBetCommand(BetStatus.WON, null)))
                .isInstanceOf(RuntimeException.class);

        verify(betRepository).findByIdAndUserId(requestedBetId, AUTHENTICATED_USER_ID);
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    @Test
    void should_not_publish_when_settlement_persistence_fails() {
        var pending = pendingBet(AUTHENTICATED_USER_ID);
        when(betRepository.findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.of(pending));
        when(betRepository.save(any(Bet.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> settleBetService.settle(
                BET_ID, AUTHENTICATED_USER_ID, new SettleBetCommand(BetStatus.WON, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(betRepository).findByIdAndUserId(BET_ID, AUTHENTICATED_USER_ID);
        verify(betRepository).save(any(Bet.class));
        verifyNoMoreInteractions(betRepository);
        verifyNoInteractions(publisher);
    }

    private static Stream<Arguments> invalidCreationInputs() {
        return Stream.of(
                Arguments.of("zero stake", new BigDecimal("0"), new BigDecimal("2.10")),
                Arguments.of("negative stake", new BigDecimal("-0.01"), new BigDecimal("2.10")),
                Arguments.of("stake rounds to zero", new BigDecimal("0.004"), new BigDecimal("2.10")),
                Arguments.of("odds equal one", new BigDecimal("100.00"), new BigDecimal("1")),
                Arguments.of("odds below one", new BigDecimal("100.00"), new BigDecimal("0.99")),
                Arguments.of("odds round to one", new BigDecimal("100.00"), new BigDecimal("1.00004")));
    }

    private static Stream<Arguments> invalidUpdateInputs() {
        return invalidCreationInputs();
    }

    private static Stream<Arguments> missingOrCrossUserLookupCases() {
        return Stream.of(
                Arguments.of("missing Bet", UUID.randomUUID()),
                Arguments.of("cross-user Bet", UUID.randomUUID()));
    }

    private static Stream<Arguments> successfulSettlements() {
        return Stream.of(
                Arguments.of("WON", BetStatus.WON, new BigDecimal("3.3333"),
                        new BigDecimal("77.77"), new BigDecimal("181.45"),
                        new BigDecimal("259.22"), null),
                Arguments.of("LOST", BetStatus.LOST, new BigDecimal("3.3333"),
                        new BigDecimal("77.77"), new BigDecimal("-77.77"),
                        new BigDecimal("0.00"), null),
                Arguments.of("VOID", BetStatus.VOID, new BigDecimal("3.3333"),
                        new BigDecimal("77.77"), new BigDecimal("0.00"),
                        new BigDecimal("77.77"), null),
                Arguments.of("CASHOUT", BetStatus.CASHOUT, new BigDecimal("3.3333"),
                        new BigDecimal("77.77"), new BigDecimal("11.11"),
                        new BigDecimal("88.88"), new BigDecimal("130.00")),
                Arguments.of("CANCELLED", BetStatus.CANCELLED, new BigDecimal("3.3333"),
                        new BigDecimal("77.77"), new BigDecimal("0.00"),
                        new BigDecimal("77.77"), null));
    }

    private static Stream<Arguments> finalStatuses() {
        return Stream.of(
                Arguments.of("WON -> WON", BetStatus.WON),
                Arguments.of("LOST -> LOST", BetStatus.LOST),
                Arguments.of("VOID -> VOID", BetStatus.VOID),
                Arguments.of("CASHOUT -> CASHOUT", BetStatus.CASHOUT),
                Arguments.of("CANCELLED -> CANCELLED", BetStatus.CANCELLED));
    }

    private static void assertCreatedEvent(
            EventEnvelope envelope, String routingKey, Bet persisted) {
        assertEnvelopeMetadata(envelope, EventType.BET_CREATED, persisted, persisted.createdAt());
        assertThat(routingKey).isEqualTo(MessagingConstants.BET_CREATED_ROUTING_KEY);
        assertThat(envelope.payload()).isInstanceOf(BetCreatedPayload.class);
        var payload = (BetCreatedPayload) envelope.payload();
        assertThat(payload.betId()).isEqualTo(persisted.id());
        assertThat(payload.userId()).isEqualTo(persisted.userId());
        assertThat(payload.sport()).isEqualTo(persisted.sport());
        assertThat(payload.league()).isEqualTo(persisted.league());
        assertThat(payload.homeTeam()).isEqualTo(persisted.homeTeam());
        assertThat(payload.awayTeam()).isEqualTo(persisted.awayTeam());
        assertThat(payload.market()).isEqualTo(persisted.market());
        assertThat(payload.selection()).isEqualTo(persisted.selection());
        assertThat(payload.odds()).isEqualByComparingTo(persisted.odds().value());
        assertThat(payload.stake()).isEqualByComparingTo(persisted.stake().value());
        assertThat(payload.status()).isEqualTo(com.suaposta.messaging.contract.BetStatus.PENDING);
        assertThat(payload.placedAt()).isEqualTo(persisted.placedAt());
    }

    private static void assertUpdatedEvent(
            EventEnvelope envelope, String routingKey, Bet persisted) {
        assertEnvelopeMetadata(envelope, EventType.BET_UPDATED, persisted, persisted.updatedAt());
        assertThat(routingKey).isEqualTo(MessagingConstants.BET_UPDATED_ROUTING_KEY);
        assertThat(envelope.payload()).isInstanceOf(BetUpdatedPayload.class);
        var payload = (BetUpdatedPayload) envelope.payload();
        assertThat(payload.betId()).isEqualTo(persisted.id());
        assertThat(payload.userId()).isEqualTo(persisted.userId());
        assertThat(payload.sport()).isEqualTo(persisted.sport());
        assertThat(payload.league()).isEqualTo(persisted.league());
        assertThat(payload.homeTeam()).isEqualTo(persisted.homeTeam());
        assertThat(payload.awayTeam()).isEqualTo(persisted.awayTeam());
        assertThat(payload.market()).isEqualTo(persisted.market());
        assertThat(payload.selection()).isEqualTo(persisted.selection());
        assertThat(payload.odds()).isEqualByComparingTo(persisted.odds().value());
        assertThat(payload.stake()).isEqualByComparingTo(persisted.stake().value());
        assertThat(payload.status()).isEqualTo(com.suaposta.messaging.contract.BetStatus.PENDING);
        assertThat(payload.placedAt()).isEqualTo(persisted.placedAt());
        assertThat(payload.updatedAt()).isEqualTo(persisted.updatedAt());
    }

    private static void assertSettledEvent(
            EventEnvelope envelope, String routingKey, Bet persisted) {
        assertEnvelopeMetadata(envelope, EventType.BET_SETTLED, persisted, persisted.updatedAt());
        assertThat(routingKey).isEqualTo(MessagingConstants.BET_SETTLED_ROUTING_KEY);
        assertThat(envelope.payload()).isInstanceOf(BetSettledPayload.class);
        var payload = (BetSettledPayload) envelope.payload();
        assertThat(payload.betId()).isEqualTo(persisted.id());
        assertThat(payload.userId()).isEqualTo(persisted.userId());
        assertThat(payload.status()).isEqualTo(
                com.suaposta.messaging.contract.BetStatus.valueOf(persisted.status().name()));
        assertThat(payload.odds()).isEqualByComparingTo(persisted.odds().value());
        assertThat(payload.stake()).isEqualByComparingTo(persisted.stake().value());
        assertThat(payload.profit()).isEqualByComparingTo(persisted.profit());
        assertThat(payload.returnAmount()).isEqualByComparingTo(persisted.returnAmount());
        assertThat(payload.settledAt()).isEqualTo(persisted.settledAt());
    }

    private static void assertEnvelopeMetadata(
            EventEnvelope envelope, EventType expectedType, Bet persisted, Instant expectedOccurredAt) {
        assertThat(envelope.eventId()).isNotNull().isNotEqualTo(persisted.id());
        assertThat(envelope.eventType()).isEqualTo(expectedType);
        assertThat(envelope.occurredAt()).isEqualTo(expectedOccurredAt);
        assertThat(envelope.version()).isEqualTo(MessagingConstants.VERSION_ONE);
        assertThat(envelope.producer()).isEqualTo(MessagingConstants.BETTING_SERVICE_PRODUCER);
    }

    private static CreateBetCommand createCommand() {
        return new CreateBetCommand(
                "REQUEST_SPORT", "Request League", "Request Home", "Request Away",
                "REQUEST_MARKET", "Request Selection", new BigDecimal("2.10"),
                new BigDecimal("100.00"), INPUT_PLACED_AT, "request values");
    }

    private static UpdateBetCommand updateCommand() {
        return new UpdateBetCommand(
                "REQUEST_UPDATED_SPORT", "Request Updated League", "Request Player A",
                "Request Player B", "REQUEST_UPDATED_MARKET", "Request Player A",
                new BigDecimal("2.20"), new BigDecimal("110.00"), INPUT_PLACED_AT,
                "request updated values");
    }

    private static Bet pendingBet(UUID userId) {
        return Bet.create(
                BET_ID,
                userId,
                "INPUT_SPORT",
                "Input League",
                "Input Home",
                "Input Away",
                "INPUT_MARKET",
                "Input Selection",
                new Odds(new BigDecimal("2.1000")),
                new Stake(new BigDecimal("100.00")),
                INPUT_PLACED_AT,
                "input persisted state",
                ORIGINAL_CREATED_AT);
    }

    private static Bet persistedCreatedBet() {
        return persistedCreatedBetWithId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    }

    private static Bet persistedCreatedBetWithId(UUID id) {
        return Bet.create(
                id,
                PERSISTED_USER_ID,
                "PERSISTED_SPORT",
                "Persisted League",
                "Persisted Home",
                "Persisted Away",
                "PERSISTED_MARKET",
                "Persisted Selection",
                new Odds(new BigDecimal("2.12555")),
                new Stake(new BigDecimal("120.126")),
                Instant.parse("2026-07-21T20:45:00Z"),
                "persisted create values",
                PERSISTED_CREATED_AT);
    }

    private static Bet persistedUpdatedBet() {
        return Bet.restore(
                BET_ID,
                AUTHENTICATED_USER_ID,
                "PERSISTED_UPDATED_SPORT",
                "Persisted Updated League",
                "Persisted Player A",
                "Persisted Player B",
                "PERSISTED_UPDATED_MARKET",
                "Persisted Player A",
                new Odds(new BigDecimal("3.3333")),
                new Stake(new BigDecimal("77.77")),
                BetStatus.PENDING,
                null,
                null,
                Instant.parse("2026-07-21T20:50:00Z"),
                null,
                "persisted update values",
                ORIGINAL_CREATED_AT,
                PERSISTED_UPDATED_AT);
    }

    private static Bet persistedSettledBet(
            BetStatus status,
            BigDecimal odds,
            BigDecimal stake,
            BigDecimal profit,
            BigDecimal returnAmount) {
        return Bet.restore(
                BET_ID,
                AUTHENTICATED_USER_ID,
                "PERSISTED_SETTLED_SPORT",
                "Persisted Settled League",
                "Persisted Home",
                "Persisted Away",
                "PERSISTED_SETTLED_MARKET",
                "Persisted Selection",
                new Odds(odds),
                new Stake(stake),
                status,
                profit,
                returnAmount,
                Instant.parse("2026-07-21T20:55:00Z"),
                PERSISTED_SETTLED_AT,
                "persisted settlement values",
                ORIGINAL_CREATED_AT,
                PERSISTED_UPDATED_AT);
    }

    private static Bet finalBet(UUID userId, BetStatus status) {
        return Bet.restore(
                BET_ID,
                userId,
                "FINAL_SPORT",
                "Final League",
                "Final Home",
                "Final Away",
                "FINAL_MARKET",
                "Final Selection",
                new Odds(new BigDecimal("2.1000")),
                new Stake(new BigDecimal("100.00")),
                status,
                new BigDecimal("110.00"),
                new BigDecimal("210.00"),
                INPUT_PLACED_AT,
                PERSISTED_SETTLED_AT,
                "final fixture",
                ORIGINAL_CREATED_AT,
                PERSISTED_UPDATED_AT);
    }
}
