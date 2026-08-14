package com.suaposta.betting.application.service;

import com.suaposta.betting.application.dto.BetFilters;
import com.suaposta.betting.application.dto.BetPage;
import com.suaposta.betting.application.dto.BetPageRequest;
import com.suaposta.betting.application.port.out.BetRepository;
import java.util.UUID;

public final class ListBetsService {

    private final BetRepository betRepository;

    public ListBetsService(BetRepository betRepository) {
        this.betRepository = betRepository;
    }

    public BetPage list(UUID authenticatedUserId, BetFilters filters, BetPageRequest pagination) {
        return betRepository.findAllByUserId(authenticatedUserId, filters, pagination);
    }
}
