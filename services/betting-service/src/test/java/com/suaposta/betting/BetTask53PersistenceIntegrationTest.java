package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class BetTask53PersistenceIntegrationTest {

    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startApplication() {
        context = BetTestSupport.startApplication();
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void should_persist_and_reload_an_updated_pending_bet_without_changing_identity_or_creation_time()
            throws Exception {
        var userId = UUID.randomUUID();
        var original = BetTestSupport.validCreateRequest();
        var created = BetTestSupport.createBet(context, userId, original);
        var betId = BetTestSupport.responseBetId(created);
        var before = BetTestSupport.json(created);
        var update = BetTask53HttpTestSupport.validUpdateRequest();

        var updated = BetTask53HttpTestSupport.updateBet(context, userId, betId.toString(), update);

        BetTask53HttpTestSupport.assertPendingUpdateResponse(updated, userId, betId, update, before);
        context.close();
        context = BetTestSupport.startApplication();
        var reloaded = BetTestSupport.getBet(context, userId, betId.toString());
        BetTask53HttpTestSupport.assertPendingUpdateResponse(
                reloaded, userId, betId, update, before);
    }

    @Test
    void should_persist_and_reload_domain_calculated_settlement_values_and_service_timestamps()
            throws Exception {
        var userId = UUID.randomUUID();
        var create = BetTestSupport.validCreateRequest();
        create.put("stake", new BigDecimal("10.126"));
        create.put("odds", new BigDecimal("2.12555"));
        var created = BetTestSupport.createBet(context, userId, create);
        var betId = BetTestSupport.responseBetId(created);
        var before = BetTask53HttpTestSupport.json(created);

        var settled = BetTask53HttpTestSupport.settleBet(
                context, userId, betId.toString(), BetTask53HttpTestSupport.settlement("WON"));

        BetTask53HttpTestSupport.assertSettledResponse(
                settled, userId, betId, "WON", "21.53", "11.40", "10.13", before);
        context.close();
        context = BetTestSupport.startApplication();
        var reloaded = BetTestSupport.getBet(context, userId, betId.toString());
        BetTask53HttpTestSupport.assertSettledResponse(
                reloaded, userId, betId, "WON", "21.53", "11.40", "10.13", before);
        assertThat(Instant.parse(BetTask53HttpTestSupport.json(reloaded).get("settledAt").asText()))
                .isNotNull();
    }
}
