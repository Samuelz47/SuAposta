package com.suaposta.betting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.suaposta.betting.application.dto.BetFilters;
import com.suaposta.betting.application.dto.BetPage;
import com.suaposta.betting.application.dto.BetPageRequest;
import com.suaposta.betting.application.dto.CreateBetCommand;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.domain.model.BetStatus;
import com.suaposta.betting.application.port.out.BetRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BetApplicationServicesTest {

    @Mock
    private BetRepository betRepository;

    @Mock
    private BetEventPublisher publisher;

    private CreateBetService createBetService;
    private ListBetsService listBetsService;
    private GetBetService getBetService;

    @BeforeEach
    void setUp() {
        createBetService = new CreateBetService(betRepository, publisher);
        listBetsService = new ListBetsService(betRepository);
        getBetService = new GetBetService(betRepository);
    }

    @Test
    void should_create_and_persist_a_pending_bet_owned_by_the_authenticated_user() {
        var authenticatedUserId = UUID.randomUUID();
        var command = validCommand();
        when(betRepository.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = createBetService.create(authenticatedUserId, command);

        var persisted = ArgumentCaptor.forClass(Bet.class);
        verify(betRepository).save(persisted.capture());
        assertThat(result).isSameAs(persisted.getValue());
        assertThat(persisted.getValue().userId()).isEqualTo(authenticatedUserId);
        assertThat(persisted.getValue().status()).isEqualTo(BetStatus.PENDING);
        assertThat(persisted.getValue().profit()).isNull();
        assertThat(persisted.getValue().returnAmount()).isNull();
        assertThat(persisted.getValue().settledAt()).isNull();
        assertThat(persisted.getValue().stake().value()).isEqualTo(new BigDecimal("100.00"));
        assertThat(persisted.getValue().odds().value()).isEqualTo(new BigDecimal("2.1000"));
    }

    @Test
    void should_derive_ownership_from_the_authenticated_identity_not_creation_payload() {
        var authenticatedUserId = UUID.randomUUID();
        var unrelatedClientChosenUserId = UUID.randomUUID();
        var command = validCommand();
        when(betRepository.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = createBetService.create(authenticatedUserId, command);

        assertThat(result.userId()).isEqualTo(authenticatedUserId);
        assertThat(result.userId()).isNotEqualTo(unrelatedClientChosenUserId);
        verify(betRepository).save(result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFinancialInputs")
    void should_delegate_stake_and_odds_invariants_to_the_domain_without_persisting_invalid_bets(
            String scenario,
            BigDecimal stake,
            BigDecimal odds) {
        var command = command(stake, odds);

        assertThatThrownBy(() -> createBetService.create(UUID.randomUUID(), command))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(betRepository);
    }

    private static Stream<Arguments> invalidFinancialInputs() {
        return Stream.of(
                Arguments.of("stake zero", new BigDecimal("0"), new BigDecimal("2.10")),
                Arguments.of("stake negative", new BigDecimal("-0.01"), new BigDecimal("2.10")),
                Arguments.of("stake normalizes to zero", new BigDecimal("0.004"), new BigDecimal("2.10")),
                Arguments.of("odds equal one", new BigDecimal("100.00"), new BigDecimal("1")),
                Arguments.of("odds below one", new BigDecimal("100.00"), new BigDecimal("0.99")),
                Arguments.of("odds normalize to one", new BigDecimal("100.00"), new BigDecimal("1.00004")));
    }

    @Test
    void should_forward_authenticated_owner_filters_and_pagination_to_the_scoped_listing_query() {
        var authenticatedUserId = UUID.randomUUID();
        var filters = documentedFilters();
        var pagination = new BetPageRequest(2, 7);
        var expected = new BetPage(List.of(), 2, 7, 0, 0);
        when(betRepository.findAllByUserId(authenticatedUserId, filters, pagination))
                .thenReturn(expected);

        var result = listBetsService.list(authenticatedUserId, filters, pagination);

        assertThat(result).isSameAs(expected);
        verify(betRepository).findAllByUserId(authenticatedUserId, filters, pagination);
        verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_return_only_the_result_of_the_ownership_scoped_listing_query() {
        var authenticatedUserId = UUID.randomUUID();
        var anotherUserId = UUID.randomUUID();
        var filters = documentedFilters();
        var pagination = new BetPageRequest(0, 20);
        var ownedBet = pendingBet(authenticatedUserId);
        var anotherUsersBet = pendingBet(anotherUserId);
        var expected = new BetPage(List.of(ownedBet), 0, 20, 1, 1);
        when(betRepository.findAllByUserId(authenticatedUserId, filters, pagination))
                .thenReturn(expected);

        var result = listBetsService.list(authenticatedUserId, filters, pagination);

        assertThat(result.content()).containsExactly(ownedBet).doesNotContain(anotherUsersBet);
        verify(betRepository).findAllByUserId(authenticatedUserId, filters, pagination);
        verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_return_owned_bet_when_lookup_uses_bet_id_and_authenticated_user_id() {
        var authenticatedUserId = UUID.randomUUID();
        var bet = pendingBet(authenticatedUserId);
        when(betRepository.findByIdAndUserId(bet.id(), authenticatedUserId))
                .thenReturn(Optional.of(bet));

        var result = getBetService.get(bet.id(), authenticatedUserId);

        assertThat(result).isSameAs(bet);
        verify(betRepository).findByIdAndUserId(bet.id(), authenticatedUserId);
        verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_produce_application_not_found_when_owned_lookup_returns_no_bet() {
        var authenticatedUserId = UUID.randomUUID();
        var missingBetId = UUID.randomUUID();
        when(betRepository.findByIdAndUserId(missingBetId, authenticatedUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> getBetService.get(missingBetId, authenticatedUserId))
                .isInstanceOf(RuntimeException.class);

        verify(betRepository).findByIdAndUserId(missingBetId, authenticatedUserId);
        verifyNoMoreInteractions(betRepository);
    }

    @Test
    void should_make_cross_user_and_missing_bets_indistinguishable_at_the_application_boundary() {
        var ownerId = UUID.randomUUID();
        var requestingUserId = UUID.randomUUID();
        var anotherUsersBet = pendingBet(ownerId);
        var missingBetId = UUID.randomUUID();
        when(betRepository.findByIdAndUserId(anotherUsersBet.id(), requestingUserId))
                .thenReturn(Optional.empty());
        when(betRepository.findByIdAndUserId(missingBetId, requestingUserId)).thenReturn(Optional.empty());

        var crossUserFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> getBetService.get(anotherUsersBet.id(), requestingUserId));
        var missingFailure = org.assertj.core.api.Assertions.catchThrowable(
                () -> getBetService.get(missingBetId, requestingUserId));

        assertThat(crossUserFailure).isNotNull();
        assertThat(missingFailure).isNotNull();
        assertThat(crossUserFailure.getClass()).isEqualTo(missingFailure.getClass());
        assertThat(crossUserFailure.getMessage()).isEqualTo(missingFailure.getMessage());
        verify(betRepository).findByIdAndUserId(anotherUsersBet.id(), requestingUserId);
        verify(betRepository).findByIdAndUserId(missingBetId, requestingUserId);
        verifyNoMoreInteractions(betRepository);
        assertThat(ownerId).isNotEqualTo(requestingUserId);
    }

    private static Bet pendingBet(UUID userId) {
        var fixtureRepository = org.mockito.Mockito.mock(BetRepository.class);
        var fixturePublisher = org.mockito.Mockito.mock(BetEventPublisher.class);
        when(fixtureRepository.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new CreateBetService(fixtureRepository, fixturePublisher).create(userId, validCommand());
    }

    private static CreateBetCommand validCommand() {
        return command(new BigDecimal("100.00"), new BigDecimal("2.10"));
    }

    private static CreateBetCommand command(BigDecimal stake, BigDecimal odds) {
        return new CreateBetCommand(
                "FOOTBALL",
                "Brasileirão Série A",
                "Fortaleza",
                "Bahia",
                "MATCH_RESULT",
                "Fortaleza",
                odds,
                stake,
                Instant.parse("2026-07-21T20:30:00Z"),
                "Application unit fixture");
    }

    private static BetFilters documentedFilters() {
        return new BetFilters(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-31T23:59:59Z"),
                "FOOTBALL",
                "Brasileirão Série A",
                "Fortaleza",
                "MATCH_RESULT",
                BetStatus.PENDING,
                new BigDecimal("1.50"),
                new BigDecimal("4.00"),
                new BigDecimal("25.00"),
                new BigDecimal("250.00"));
    }
}
