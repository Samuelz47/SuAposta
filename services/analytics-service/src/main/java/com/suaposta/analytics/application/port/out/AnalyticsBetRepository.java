package com.suaposta.analytics.application.port.out;

import com.suaposta.analytics.application.model.AnalyticsBet;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsBetRepository {

    Optional<AnalyticsBet> findByBetId(UUID betId);

    AnalyticsBet insert(AnalyticsBet analyticsBet);

    AnalyticsBet update(AnalyticsBet analyticsBet);
}
