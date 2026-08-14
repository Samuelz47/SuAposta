package com.suaposta.betting.presentation.dto;

import com.suaposta.betting.domain.model.BetStatus;
import java.math.BigDecimal;

public record SettleBetRequest(
        BetStatus status,
        BigDecimal returnAmount) {
}
