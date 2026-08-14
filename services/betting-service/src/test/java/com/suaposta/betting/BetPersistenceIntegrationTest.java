package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class BetPersistenceIntegrationTest {

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
    void should_preserve_identity_ownership_pending_state_precision_and_nulls_after_context_restart()
            throws Exception {
        var userId = UUID.randomUUID();
        var request = BetTestSupport.createRequest(
                "FOOTBALL",
                "Persistence League",
                "Home Team",
                "Away Team",
                "MATCH_RESULT",
                "Home Team",
                new BigDecimal("2.12555"),
                new BigDecimal("10.126"),
                "2026-07-21T20:30:00Z",
                "persistence round-trip fixture");

        var created = BetTestSupport.createBet(context, userId, request);

        BetTestSupport.assertCreatedBetResponse(created, userId, request, "10.13", "2.1256");
        var betId = BetTestSupport.responseBetId(created);

        context.close();
        context = BetTestSupport.startApplication();

        var retrieved = BetTestSupport.getBet(context, userId, betId.toString());

        BetTestSupport.assertRetrievedBetResponse(
                retrieved, userId, request, "10.13", "2.1256");
        assertThat(BetTestSupport.json(retrieved).get("id").asText()).isEqualTo(betId.toString());
    }
}
