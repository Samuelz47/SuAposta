package com.suaposta.betting.infrastructure.persistence;

import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.domain.model.BetStatus;
import com.suaposta.betting.domain.model.Odds;
import com.suaposta.betting.domain.model.Stake;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bets")
public class BetEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "sport", nullable = false, length = 100)
    private String sport;

    @Column(name = "league", nullable = false, length = 255)
    private String league;

    @Column(name = "home_team", nullable = false, length = 255)
    private String homeTeam;

    @Column(name = "away_team", nullable = false, length = 255)
    private String awayTeam;

    @Column(name = "market", nullable = false, length = 100)
    private String market;

    @Column(name = "selection", nullable = false, length = 255)
    private String selection;

    @Column(name = "odds", nullable = false, precision = 19, scale = 4)
    private BigDecimal odds;

    @Column(name = "stake", nullable = false, precision = 19, scale = 2)
    private BigDecimal stake;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BetStatus status;

    @Column(name = "profit", precision = 19, scale = 2)
    private BigDecimal profit;

    @Column(name = "return_amount", precision = 19, scale = 2)
    private BigDecimal returnAmount;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BetEntity() {
    }

    BetEntity(Bet bet) {
        id = bet.id();
        userId = bet.userId();
        sport = bet.sport();
        league = bet.league();
        homeTeam = bet.homeTeam();
        awayTeam = bet.awayTeam();
        market = bet.market();
        selection = bet.selection();
        odds = bet.odds().value();
        stake = bet.stake().value();
        status = bet.status();
        profit = bet.profit();
        returnAmount = bet.returnAmount();
        placedAt = bet.placedAt();
        settledAt = bet.settledAt();
        notes = bet.notes();
        createdAt = bet.createdAt();
        updatedAt = bet.updatedAt();
    }

    Bet toDomain() {
        return Bet.restore(
                id,
                userId,
                sport,
                league,
                homeTeam,
                awayTeam,
                market,
                selection,
                new Odds(odds),
                new Stake(stake),
                status,
                profit,
                returnAmount,
                placedAt,
                settledAt,
                notes,
                createdAt,
                updatedAt);
    }
}
