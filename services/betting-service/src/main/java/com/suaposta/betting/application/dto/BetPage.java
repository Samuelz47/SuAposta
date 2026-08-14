package com.suaposta.betting.application.dto;

import com.suaposta.betting.domain.model.Bet;
import java.util.List;

public record BetPage(
        List<Bet> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
