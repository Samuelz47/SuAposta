package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.domain.model.BetStatus;
import com.suaposta.betting.domain.model.Odds;
import com.suaposta.betting.domain.model.Stake;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class BetTask53PersistencePortIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-21T21:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-21T21:20:00Z");
    private static final Instant SETTLED_AT = Instant.parse("2026-07-21T22:00:00Z");

    private static ConfigurableApplicationContext context;
    private static BetRepository repository;

    @BeforeAll
    static void startApplicationAndInitializeRepository() {
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
    void should_save_and_reload_an_updated_pending_bet_through_the_ownership_scoped_repository() {
        var userId = UUID.randomUUID();
        var original = pendingBet(userId, "100.00", "2.1000", CREATED_AT, CREATED_AT);
        repository.save(original);
        var updated = Bet.restore(
                original.id(), original.userId(), "TENNIS", "Updated League", "Player A", "Player B",
                "MATCH_WINNER", "Player A", new Odds(new BigDecimal("2.12555")),
                new Stake(new BigDecimal("120.126")), BetStatus.PENDING, null, null,
                original.placedAt(), null, "updated notes", original.createdAt(), UPDATED_AT);

        repository.save(updated);
        var reloaded = repository.findByIdAndUserId(updated.id(), userId).orElseThrow();

        assertThat(reloaded.id()).isEqualTo(original.id());
        assertThat(reloaded.userId()).isEqualTo(userId);
        assertThat(reloaded.sport()).isEqualTo("TENNIS");
        assertThat(reloaded.league()).isEqualTo("Updated League");
        assertThat(reloaded.homeTeam()).isEqualTo("Player A");
        assertThat(reloaded.awayTeam()).isEqualTo("Player B");
        assertThat(reloaded.market()).isEqualTo("MATCH_WINNER");
        assertThat(reloaded.selection()).isEqualTo("Player A");
        assertThat(reloaded.stake().value()).isEqualTo(new BigDecimal("120.13"));
        assertThat(reloaded.stake().value().scale()).isEqualTo(2);
        assertThat(reloaded.odds().value()).isEqualTo(new BigDecimal("2.1256"));
        assertThat(reloaded.odds().value().scale()).isEqualTo(4);
        assertThat(reloaded.status()).isEqualTo(BetStatus.PENDING);
        assertThat(reloaded.profit()).isNull();
        assertThat(reloaded.returnAmount()).isNull();
        assertThat(reloaded.settledAt()).isNull();
        assertThat(reloaded.createdAt()).isEqualTo(CREATED_AT);
        assertThat(reloaded.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void should_save_and_reload_a_settled_bet_with_final_financial_values_and_timestamps() {
        var userId = UUID.randomUUID();
        var pending = pendingBet(userId, "100.00", "2.1000", CREATED_AT, CREATED_AT);
        var settled = Bet.restore(
                pending.id(), pending.userId(), pending.sport(), pending.league(), pending.homeTeam(),
                pending.awayTeam(), pending.market(), pending.selection(), pending.odds(), pending.stake(),
                BetStatus.WON, new BigDecimal("110.00"), new BigDecimal("210.00"), pending.placedAt(),
                SETTLED_AT, pending.notes(), pending.createdAt(), UPDATED_AT);

        repository.save(settled);
        var reloaded = repository.findByIdAndUserId(settled.id(), userId).orElseThrow();

        assertThat(reloaded.id()).isEqualTo(settled.id());
        assertThat(reloaded.userId()).isEqualTo(userId);
        assertThat(reloaded.status()).isEqualTo(BetStatus.WON);
        assertThat(reloaded.profit()).isEqualTo(new BigDecimal("110.00"));
        assertThat(reloaded.profit().scale()).isEqualTo(2);
        assertThat(reloaded.returnAmount()).isEqualTo(new BigDecimal("210.00"));
        assertThat(reloaded.returnAmount().scale()).isEqualTo(2);
        assertThat(reloaded.settledAt()).isEqualTo(SETTLED_AT);
        assertThat(reloaded.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(reloaded.createdAt()).isEqualTo(CREATED_AT);
    }

    private static Bet pendingBet(
            UUID userId, String stake, String odds, Instant createdAt, Instant updatedAt) {
        return Bet.restore(
                UUID.randomUUID(), userId, "FOOTBALL", "League", "Home", "Away", "MATCH_RESULT",
                "Home", new Odds(new BigDecimal(odds)), new Stake(new BigDecimal(stake)),
                BetStatus.PENDING, null, null, Instant.parse("2026-07-21T20:30:00Z"), null,
                "persistence fixture", createdAt, updatedAt);
    }
}
