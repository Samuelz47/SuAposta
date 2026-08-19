package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.suaposta.betting.application.dto.BetFilters;
import com.suaposta.betting.application.dto.BetPageRequest;
import com.suaposta.betting.application.dto.CreateBetCommand;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.betting.application.service.CreateBetService;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.domain.model.BetStatus;
import com.suaposta.betting.application.port.out.BetRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;

class BetRepositoryIntegrationTest {

    private static ConfigurableApplicationContext context;
    private static BetRepository repository;

    @BeforeAll
    static void startApplicationAndInitializeSchema() {
        context = BetTestSupport.startApplication();
        repository = context.getBean(BetRepository.class);
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void should_initialize_migration_schema_and_save_reload_a_pending_bet_with_exact_precision()
            throws Exception {
        var userId = UUID.randomUUID();
        var bet = pendingBet(
                userId,
                "FOOTBALL",
                "Persistence League",
                "Home Team",
                "Away Team",
                "MATCH_RESULT",
                "Home Team",
                "2.12555",
                "10.126",
                "2026-07-21T20:30:00Z");

        var saved = repository.save(bet);
        var reloaded = repository.findByIdAndUserId(saved.id(), userId);

        assertThat(reloaded).isPresent();
        var persisted = reloaded.orElseThrow();
        assertThat(persisted.id()).isEqualTo(saved.id());
        assertThat(persisted.userId()).isEqualTo(userId);
        assertThat(persisted.status()).isEqualTo(BetStatus.PENDING);
        assertThat(persisted.stake().value()).isEqualTo(new BigDecimal("10.13"));
        assertThat(persisted.stake().value().scale()).isEqualTo(2);
        assertThat(persisted.odds().value()).isEqualTo(new BigDecimal("2.1256"));
        assertThat(persisted.odds().value().scale()).isEqualTo(4);
        assertThat(persisted.profit()).isNull();
        assertThat(persisted.returnAmount()).isNull();
        assertThat(persisted.settledAt()).isNull();
        assertThat(persisted.createdAt()).isEqualTo(saved.createdAt());
        assertThat(persisted.updatedAt()).isEqualTo(saved.updatedAt());
    }

    @Test
    void should_retrieve_by_bet_id_and_owner_and_return_empty_for_the_same_id_with_another_user()
            throws Exception {
        var ownerId = UUID.randomUUID();
        var anotherUserId = UUID.randomUUID();
        var saved = repository.save(defaultBet(ownerId));

        var ownedLookup = repository.findByIdAndUserId(saved.id(), ownerId);
        var crossUserLookup = repository.findByIdAndUserId(saved.id(), anotherUserId);

        assertThat(ownedLookup).isPresent();
        assertThat(ownedLookup.orElseThrow().id()).isEqualTo(saved.id());
        assertThat(crossUserLookup).isEmpty();
    }

    @Test
    void should_list_only_bets_owned_by_the_requested_user() throws Exception {
        var userId = UUID.randomUUID();
        var anotherUserId = UUID.randomUUID();
        var firstOwned = repository.save(defaultBet(userId));
        var secondOwned = repository.save(pendingBet(
                userId,
                "TENNIS",
                "ATP",
                "Player A",
                "Player B",
                "MATCH_WINNER",
                "Player A",
                "3.0000",
                "50.00",
                "2026-07-22T20:30:00Z"));
        var anotherUsersBet = repository.save(defaultBet(anotherUserId));

        var result = repository.findAllByUserId(
                userId, BetFilters.empty(), new BetPageRequest(0, 20));

        assertThat(ids(result.content())).containsExactlyInAnyOrder(firstOwned.id(), secondOwned.id());
        assertThat(ids(result.content())).doesNotContain(anotherUsersBet.id());
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentedFilterCases")
    void should_apply_documented_filter_within_the_ownership_scoped_persistence_query(
            String scenario,
            BetFilters filters,
            String expectedOwnedMatches) throws Exception {
        var userId = UUID.randomUUID();
        var anotherUserId = UUID.randomUUID();
        var firstOwned = repository.save(pendingBet(
                userId,
                "FOOTBALL",
                "Alpha League",
                "Team A",
                "Team B",
                "MATCH_RESULT",
                "Team A",
                "2.1000",
                "100.00",
                "2026-07-10T20:30:00Z"));
        var secondOwned = repository.save(pendingBet(
                userId,
                "TENNIS",
                "Beta League",
                "Team C",
                "Team D",
                "MATCH_WINNER",
                "Team C",
                "3.0000",
                "50.00",
                "2026-07-20T20:30:00Z"));
        var anotherUsersMatchingBet = repository.save("SECOND".equals(expectedOwnedMatches)
                ? pendingBet(
                        anotherUserId,
                        "TENNIS",
                        "Beta League",
                        "Team C",
                        "Team D",
                        "MATCH_WINNER",
                        "Team C",
                        "3.0000",
                        "50.00",
                        "2026-07-20T20:30:00Z")
                : pendingBet(
                        anotherUserId,
                        "FOOTBALL",
                        "Alpha League",
                        "Team A",
                        "Team B",
                        "MATCH_RESULT",
                        "Team A",
                        "2.1000",
                        "100.00",
                        "2026-07-10T20:30:00Z"));

        var result = repository.findAllByUserId(userId, filters, new BetPageRequest(0, 20));

        var expectedIds = switch (expectedOwnedMatches) {
            case "FIRST" -> Set.of(firstOwned.id());
            case "SECOND" -> Set.of(secondOwned.id());
            case "BOTH" -> Set.of(firstOwned.id(), secondOwned.id());
            default -> throw new AssertionError("Unknown fixture expectation: " + expectedOwnedMatches);
        };
        assertThat(ids(result.content())).containsExactlyInAnyOrderElementsOf(expectedIds);
        assertThat(ids(result.content())).doesNotContain(anotherUsersMatchingBet.id());
    }

    private static Stream<Arguments> documentedFilterCases() {
        return Stream.of(
                Arguments.of("startDate", filters(
                        Instant.parse("2026-07-15T00:00:00Z"), null, null, null, null, null, null,
                        null, null, null, null), "SECOND"),
                Arguments.of("endDate", filters(
                        null, Instant.parse("2026-07-15T00:00:00Z"), null, null, null, null, null,
                        null, null, null, null), "FIRST"),
                Arguments.of("sport", filters(
                        null, null, "FOOTBALL", null, null, null, null,
                        null, null, null, null), "FIRST"),
                Arguments.of("league", filters(
                        null, null, null, "Alpha League", null, null, null,
                        null, null, null, null), "FIRST"),
                Arguments.of("team", filters(
                        null, null, null, null, "Team A", null, null,
                        null, null, null, null), "FIRST"),
                Arguments.of("market", filters(
                        null, null, null, null, null, "MATCH_RESULT", null,
                        null, null, null, null), "FIRST"),
                Arguments.of("status", filters(
                        null, null, null, null, null, null, BetStatus.PENDING,
                        null, null, null, null), "BOTH"),
                Arguments.of("minOdds", filters(
                        null, null, null, null, null, null, null,
                        new BigDecimal("2.20"), null, null, null), "SECOND"),
                Arguments.of("maxOdds", filters(
                        null, null, null, null, null, null, null,
                        null, new BigDecimal("2.20"), null, null), "FIRST"),
                Arguments.of("minStake", filters(
                        null, null, null, null, null, null, null,
                        null, null, new BigDecimal("75.00"), null), "FIRST"),
                Arguments.of("maxStake", filters(
                        null, null, null, null, null, null, null,
                        null, null, null, new BigDecimal("75.00")), "SECOND"));
    }

    @Test
    void should_apply_explicit_pagination_after_ownership_constraint() throws Exception {
        var userId = UUID.randomUUID();
        var anotherUserId = UUID.randomUUID();
        var firstOwned = repository.save(defaultBet(userId));
        var secondOwned = repository.save(pendingBet(
                userId,
                "TENNIS",
                "Pagination League",
                "Player A",
                "Player B",
                "MATCH_WINNER",
                "Player A",
                "3.0000",
                "50.00",
                "2026-07-22T20:30:00Z"));
        var anotherUsersBet = repository.save(defaultBet(anotherUserId));

        var firstPage = repository.findAllByUserId(
                userId, BetFilters.empty(), new BetPageRequest(0, 1));
        var secondPage = repository.findAllByUserId(
                userId, BetFilters.empty(), new BetPageRequest(1, 1));

        assertThat(firstPage.page()).isZero();
        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(firstPage.size()).isEqualTo(1);
        assertThat(secondPage.size()).isEqualTo(1);
        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(secondPage.totalElements()).isEqualTo(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(secondPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.content()).hasSize(1);
        assertThat(secondPage.content()).hasSize(1);
        assertThat(ids(firstPage.content())).doesNotContain(anotherUsersBet.id());
        assertThat(ids(secondPage.content())).doesNotContain(anotherUsersBet.id());
        assertThat(Stream.concat(firstPage.content().stream(), secondPage.content().stream())
                .map(Bet::id))
                .containsExactlyInAnyOrder(firstOwned.id(), secondOwned.id());
    }

    private static Bet defaultBet(UUID userId) {
        return pendingBet(
                userId,
                "FOOTBALL",
                "Persistence League",
                "Home Team",
                "Away Team",
                "MATCH_RESULT",
                "Home Team",
                "2.1000",
                "100.00",
                "2026-07-21T20:30:00Z");
    }

    private static Bet pendingBet(
            UUID userId,
            String sport,
            String league,
            String homeTeam,
            String awayTeam,
            String market,
            String selection,
            String odds,
            String stake,
            String placedAt) {
        var capturingPort = mock(BetRepository.class);
        var publisher = mock(BetEventPublisher.class);
        when(capturingPort.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CreateBetService(capturingPort, publisher);
        return service.create(userId, new CreateBetCommand(
                sport,
                league,
                homeTeam,
                awayTeam,
                market,
                selection,
                new BigDecimal(odds),
                new BigDecimal(stake),
                Instant.parse(placedAt),
                "Direct repository fixture " + UUID.randomUUID()));
    }

    private static BetFilters filters(
            Instant startDate,
            Instant endDate,
            String sport,
            String league,
            String team,
            String market,
            BetStatus status,
            BigDecimal minOdds,
            BigDecimal maxOdds,
            BigDecimal minStake,
            BigDecimal maxStake) {
        return new BetFilters(
                startDate,
                endDate,
                sport,
                league,
                team,
                market,
                status,
                minOdds,
                maxOdds,
                minStake,
                maxStake);
    }

    private static Set<UUID> ids(java.util.List<Bet> bets) {
        var ids = new HashSet<UUID>();
        bets.forEach(bet -> ids.add(bet.id()));
        return ids;
    }
}
