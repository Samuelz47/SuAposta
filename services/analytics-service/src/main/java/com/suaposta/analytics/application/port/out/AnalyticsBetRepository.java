package com.suaposta.analytics.application.port.out;

import com.suaposta.analytics.application.model.AnalyticsBet;
import com.suaposta.analytics.application.model.BankrollEvolutionFilters;
import com.suaposta.analytics.application.model.DashboardFilters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsBetRepository {

    List<AnalyticsBet> findDashboardBets(UUID userId, DashboardFilters filters);

    List<AnalyticsBet> findBankrollEvolutionBets(UUID userId, BankrollEvolutionFilters filters);

    Optional<AnalyticsBet> findByBetId(UUID betId);

    AnalyticsBet insert(AnalyticsBet analyticsBet);

    AnalyticsBet update(AnalyticsBet analyticsBet);
}
