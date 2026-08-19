package com.suaposta.betting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.suaposta.betting.application.dto.CreateBetCommand;
import com.suaposta.betting.application.dto.SettleBetCommand;
import com.suaposta.betting.application.dto.UpdateBetCommand;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.domain.model.BetStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class BetTask53ApplicationServicesTest {

    private static final Instant NOW = Instant.parse("2026-07-21T22:00:00Z");

    @Mock
    private BetRepository betRepository;

    @Mock
    private BetEventPublisher publisher;

    private UpdateBetService updateBetService;
    private SettleBetService settleBetService;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        updateBetService = new UpdateBetService(betRepository, clock, publisher);
        settleBetService = new SettleBetService(betRepository, clock, publisher);
    }

    @Test
    void should_update_an_owned_pending_bet_using_the_scoped_lookup_and_persist_all_mutable_fields() {
        var userId = UUID.randomUUID();
        var bet = pendingBet(userId);
        var command = updatedCommand();
        when(betRepository.findByIdAndUserId(bet.id(), userId)).thenReturn(Optional.of(bet));
        when(betRepository.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = updateBetService.update(bet.id(), userId, command);

        var saved = ArgumentCaptor.forClass(Bet.class);
        verify(betRepository).findByIdAndUserId(bet.id(), userId);
        verify(betRepository).save(saved.capture());
        assertThat(result).isSameAs(saved.getValue());
        assertThat(saved.getValue().id()).isEqualTo(bet.id());
        assertThat(saved.getValue().userId()).isEqualTo(userId);
        assertThat(saved.getValue().sport()).isEqualTo(command.sport());
        assertThat(saved.getValue().league()).isEqualTo(command.league());
        assertThat(saved.getValue().homeTeam()).isEqualTo(command.homeTeam());
        assertThat(saved.getValue().awayTeam()).isEqualTo(command.awayTeam());
        assertThat(saved.getValue().market()).isEqualTo(command.market());
        assertThat(saved.getValue().selection()).isEqualTo(command.selection());
        assertThat(saved.getValue().stake().value()).isEqualTo(new BigDecimal("120.13"));
        assertThat(saved.getValue().odds().value()).isEqualTo(new BigDecimal("2.1256"));
        assertThat(saved.getValue().placedAt()).isEqualTo(command.placedAt());
        assertThat(saved.getValue().notes()).isEqualTo(command.notes());
        assertThat(saved.getValue().status()).isEqualTo(BetStatus.PENDING);
        assertThat(saved.getValue().profit()).isNull();
        assertThat(saved.getValue().returnAmount()).isNull();
        assertThat(saved.getValue().settledAt()).isNull();
        assertThat(saved.getValue().createdAt()).isEqualTo(bet.createdAt());
        assertThat(saved.getValue().updatedAt()).isEqualTo(NOW);
        verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_return_the_same_not_found_behavior_for_missing_and_cross_user_update_lookup() {
        var requestedUserId = UUID.randomUUID();
        var missingId = UUID.randomUUID();
        var crossUserId = UUID.randomUUID();
        when(betRepository.findByIdAndUserId(missingId, requestedUserId)).thenReturn(Optional.empty());
        when(betRepository.findByIdAndUserId(crossUserId, requestedUserId)).thenReturn(Optional.empty());

        var missing = org.assertj.core.api.Assertions.catchThrowable(
                () -> updateBetService.update(missingId, requestedUserId, updatedCommand()));
        var crossUser = org.assertj.core.api.Assertions.catchThrowable(
                () -> updateBetService.update(crossUserId, requestedUserId, updatedCommand()));

        assertThat(missing).isNotNull();
        assertThat(crossUser).isNotNull();
        assertThat(crossUser.getClass()).isEqualTo(missing.getClass());
        assertThat(crossUser.getMessage()).isEqualTo(missing.getMessage());
        verify(betRepository).findByIdAndUserId(missingId, requestedUserId);
        verify(betRepository).findByIdAndUserId(crossUserId, requestedUserId);
        verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_return_the_same_not_found_behavior_for_missing_and_cross_user_settlement_lookup() {
        var requestedUserId = UUID.randomUUID();
        var missingBetId = UUID.randomUUID();
        var crossUserBetId = UUID.randomUUID();
        var settlement = new SettleBetCommand(BetStatus.WON, null);
        when(betRepository.findByIdAndUserId(missingBetId, requestedUserId)).thenReturn(Optional.empty());
        when(betRepository.findByIdAndUserId(crossUserBetId, requestedUserId)).thenReturn(Optional.empty());

        var missingFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> settleBetService.settle(missingBetId, requestedUserId, settlement));
        var crossUserFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> settleBetService.settle(crossUserBetId, requestedUserId, settlement));

        assertThat(missingFailure).isNotNull();
        assertThat(crossUserFailure).isNotNull();
        assertThat(crossUserFailure.getClass()).isEqualTo(missingFailure.getClass());
        assertThat(crossUserFailure.getMessage()).isEqualTo(missingFailure.getMessage());
        verify(betRepository).findByIdAndUserId(missingBetId, requestedUserId);
        verify(betRepository).findByIdAndUserId(crossUserBetId, requestedUserId);
        org.mockito.Mockito.verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_reject_update_of_a_final_bet_without_saving_or_mutating_it() {
        var userId = UUID.randomUUID();
        var bet = settledBet(userId, BetStatus.WON, new BigDecimal("110.00"), new BigDecimal("210.00"));
        var before = snapshot(bet);
        when(betRepository.findByIdAndUserId(bet.id(), userId)).thenReturn(Optional.of(bet));

        assertThatThrownBy(() -> updateBetService.update(bet.id(), userId, updatedCommand()))
                .isInstanceOf(RuntimeException.class);

        assertThat(snapshot(bet)).isEqualTo(before);
        verify(betRepository).findByIdAndUserId(bet.id(), userId);
        verifyNoMoreInteractions(betRepository);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUpdateFinancialInputs")
    void should_reject_invalid_update_financial_input_without_saving(
            String scenario, BigDecimal stake, BigDecimal odds) {
        var userId = UUID.randomUUID();
        var bet = pendingBet(userId);
        when(betRepository.findByIdAndUserId(bet.id(), userId)).thenReturn(Optional.of(bet));
        var command = new UpdateBetCommand(
                "TENNIS", "Updated League", "Player A", "Player B", "MATCH_WINNER", "Player A",
                odds, stake, Instant.parse("2026-07-22T20:30:00Z"), "updated notes");

        assertThatThrownBy(() -> updateBetService.update(bet.id(), userId, command))
                .isInstanceOf(RuntimeException.class);

        assertThat(snapshot(bet).get(10)).isEqualTo(BetStatus.PENDING);
        assertThat(snapshot(bet).get(11)).isNull();
        assertThat(snapshot(bet).get(12)).isNull();
        assertThat(snapshot(bet).get(14)).isNull();
        verify(betRepository).findByIdAndUserId(bet.id(), userId);
        verifyNoMoreInteractions(betRepository);
    }

    private static Stream<Arguments> invalidUpdateFinancialInputs() {
        return Stream.of(
                Arguments.of("zero stake", new BigDecimal("0"), new BigDecimal("2.10")),
                Arguments.of("negative stake", new BigDecimal("-0.01"), new BigDecimal("2.10")),
                Arguments.of("stake rounds to zero", new BigDecimal("0.004"), new BigDecimal("2.10")),
                Arguments.of("odds equal one", new BigDecimal("100.00"), new BigDecimal("1")),
                Arguments.of("odds below one", new BigDecimal("100.00"), new BigDecimal("0.99")),
                Arguments.of("odds rounds to one", new BigDecimal("100.00"), new BigDecimal("1.00004")));
    }

    @Test
    void should_delegate_won_settlement_to_the_domain_and_persist_its_derived_result() {
        var userId = UUID.randomUUID();
        var bet = pendingBet(userId);
        when(betRepository.findByIdAndUserId(bet.id(), userId)).thenReturn(Optional.of(bet));
        when(betRepository.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = settleBetService.settle(
                bet.id(), userId, new SettleBetCommand(BetStatus.WON, null));

        var saved = ArgumentCaptor.forClass(Bet.class);
        verify(betRepository).findByIdAndUserId(bet.id(), userId);
        verify(betRepository).save(saved.capture());
        assertThat(result).isSameAs(saved.getValue());
        assertThat(saved.getValue().status()).isEqualTo(BetStatus.WON);
        assertThat(saved.getValue().profit()).isEqualByComparingTo("110.00");
        assertThat(saved.getValue().returnAmount()).isEqualByComparingTo("210.00");
        assertThat(saved.getValue().settledAt()).isEqualTo(NOW);
        assertThat(saved.getValue().createdAt()).isEqualTo(bet.createdAt());
        assertThat(saved.getValue().updatedAt()).isEqualTo(NOW);
        verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_reject_missing_cashout_return_without_saving_or_mutating_the_pending_bet() {
        var userId = UUID.randomUUID();
        var bet = pendingBet(userId);
        var before = snapshot(bet);
        when(betRepository.findByIdAndUserId(bet.id(), userId)).thenReturn(Optional.of(bet));

        assertThatThrownBy(() -> settleBetService.settle(
                bet.id(), userId, new SettleBetCommand(BetStatus.CASHOUT, null)))
                .isInstanceOf(RuntimeException.class);

        assertThat(snapshot(bet)).isEqualTo(before);
        verify(betRepository).findByIdAndUserId(bet.id(), userId);
        verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_reject_settlement_of_a_final_bet_without_saving_or_mutating_its_result() {
        var userId = UUID.randomUUID();
        var bet = settledBet(userId, BetStatus.CASHOUT, new BigDecimal("30.00"), new BigDecimal("130.00"));
        var before = snapshot(bet);
        when(betRepository.findByIdAndUserId(bet.id(), userId)).thenReturn(Optional.of(bet));

        assertThatThrownBy(() -> settleBetService.settle(
                bet.id(), userId, new SettleBetCommand(BetStatus.LOST, null)))
                .isInstanceOf(RuntimeException.class);

        assertThat(snapshot(bet)).isEqualTo(before);
        verify(betRepository).findByIdAndUserId(bet.id(), userId);
        verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_reject_pending_to_pending_without_saving_or_mutating_the_pending_bet() {
        var userId = UUID.randomUUID();
        var bet = pendingBet(userId);
        var before = snapshot(bet);
        when(betRepository.findByIdAndUserId(bet.id(), userId)).thenReturn(Optional.of(bet));

        assertThatThrownBy(() -> settleBetService.settle(
                bet.id(), userId, new SettleBetCommand(BetStatus.PENDING, null)))
                .isInstanceOf(RuntimeException.class);

        assertThat(snapshot(bet)).isEqualTo(before);
        verify(betRepository).findByIdAndUserId(bet.id(), userId);
        verifyNoMoreInteractions(betRepository);
    }

    private static Bet pendingBet(UUID userId) {
        return Bet.create(
                UUID.randomUUID(), userId, "FOOTBALL", "League", "Home", "Away",
                "MATCH_RESULT", "Home", new com.suaposta.betting.domain.model.Odds(new BigDecimal("2.1000")),
                new com.suaposta.betting.domain.model.Stake(new BigDecimal("100.00")),
                Instant.parse("2026-07-21T20:30:00Z"), "original", Instant.parse("2026-07-21T21:00:00Z"));
    }

    private static Bet settledBet(UUID userId, BetStatus status, BigDecimal profit, BigDecimal returnAmount) {
        var pending = pendingBet(userId);
        return Bet.restore(
                pending.id(), pending.userId(), pending.sport(), pending.league(), pending.homeTeam(),
                pending.awayTeam(), pending.market(), pending.selection(), pending.odds(), pending.stake(),
                status, profit, returnAmount, pending.placedAt(), NOW, pending.notes(), pending.createdAt(), NOW);
    }

    private static UpdateBetCommand updatedCommand() {
        return new UpdateBetCommand(
                "TENNIS", "Updated League", "Player A", "Player B", "MATCH_WINNER", "Player A",
                new BigDecimal("2.12555"), new BigDecimal("120.126"),
                Instant.parse("2026-07-22T20:30:00Z"), "updated notes");
    }

    private static java.util.List<Object> snapshot(Bet bet) {
        return java.util.Arrays.asList(
                bet.id(), bet.userId(), bet.sport(), bet.league(), bet.homeTeam(), bet.awayTeam(),
                bet.market(), bet.selection(), bet.stake().value(), bet.odds().value(), bet.status(),
                bet.profit(), bet.returnAmount(), bet.placedAt(), bet.settledAt(), bet.notes(),
                bet.createdAt(), bet.updatedAt());
    }
}
