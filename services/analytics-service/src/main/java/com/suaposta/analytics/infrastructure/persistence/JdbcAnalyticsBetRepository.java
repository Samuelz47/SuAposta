package com.suaposta.analytics.infrastructure.persistence;

import com.suaposta.analytics.application.model.AnalyticsBet;
import com.suaposta.analytics.application.port.out.AnalyticsBetRepository;
import com.suaposta.messaging.contract.BetStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAnalyticsBetRepository implements AnalyticsBetRepository {

    private static final String SELECT_BY_BET_ID = """
            select id, bet_id, user_id, sport, league, home_team, away_team, market, selection,
                   odds, stake, status, profit, return_amount, placed_at, settled_at, created_at, updated_at
            from analytics_bets
            where bet_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAnalyticsBetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AnalyticsBet> findByBetId(UUID betId) {
        return jdbcTemplate.query(SELECT_BY_BET_ID, JdbcAnalyticsBetRepository::mapRow, betId)
                .stream()
                .findFirst();
    }

    @Override
    public AnalyticsBet insert(AnalyticsBet analyticsBet) {
        jdbcTemplate.update("""
                        insert into analytics_bets(
                            id, bet_id, user_id, sport, league, home_team, away_team, market, selection,
                            odds, stake, status, profit, return_amount, placed_at, settled_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                analyticsBet.id(), analyticsBet.betId(), analyticsBet.userId(), analyticsBet.sport(),
                analyticsBet.league(), analyticsBet.homeTeam(), analyticsBet.awayTeam(), analyticsBet.market(),
                analyticsBet.selection(), analyticsBet.odds(), analyticsBet.stake(), analyticsBet.status().name(),
                analyticsBet.profit(), analyticsBet.returnAmount(), offset(analyticsBet.placedAt()),
                offset(analyticsBet.settledAt()), offset(analyticsBet.createdAt()), offset(analyticsBet.updatedAt()));
        return analyticsBet;
    }

    @Override
    public AnalyticsBet update(AnalyticsBet analyticsBet) {
        var rows = jdbcTemplate.update("""
                        update analytics_bets
                        set sport = ?, league = ?, home_team = ?, away_team = ?, market = ?, selection = ?,
                            odds = ?, stake = ?, status = ?, profit = ?, return_amount = ?, placed_at = ?,
                            settled_at = ?, updated_at = ?
                        where bet_id = ?
                        """,
                analyticsBet.sport(), analyticsBet.league(), analyticsBet.homeTeam(), analyticsBet.awayTeam(),
                analyticsBet.market(), analyticsBet.selection(), analyticsBet.odds(), analyticsBet.stake(),
                analyticsBet.status().name(), analyticsBet.profit(), analyticsBet.returnAmount(),
                offset(analyticsBet.placedAt()), offset(analyticsBet.settledAt()), offset(analyticsBet.updatedAt()),
                analyticsBet.betId());
        if (rows != 1) {
            throw new IllegalStateException("Analytics projection update affected " + rows + " rows");
        }
        return analyticsBet;
    }

    private static AnalyticsBet mapRow(ResultSet resultSet, int ignoredRowNumber) throws SQLException {
        return new AnalyticsBet(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("bet_id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("sport"),
                resultSet.getString("league"),
                resultSet.getString("home_team"),
                resultSet.getString("away_team"),
                resultSet.getString("market"),
                resultSet.getString("selection"),
                resultSet.getBigDecimal("odds"),
                resultSet.getBigDecimal("stake"),
                BetStatus.valueOf(resultSet.getString("status")),
                resultSet.getBigDecimal("profit"),
                resultSet.getBigDecimal("return_amount"),
                instant(resultSet, "placed_at"),
                instant(resultSet, "settled_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static OffsetDateTime offset(java.time.Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
