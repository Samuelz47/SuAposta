package com.suaposta.betting.application.dto;

import com.suaposta.betting.domain.model.BetStatus;
import java.math.BigDecimal;

public record SettleBetCommand(
        BetStatus status,
        BigDecimal returnAmount) {
}
