package com.suaposta.betting.presentation.dto;

import java.util.List;

public record BetPageResponse(
        List<BetListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
