package com.suaposta.betting.application.port.out;

import com.suaposta.betting.application.dto.BetFilters;
import com.suaposta.betting.application.dto.BetPage;
import com.suaposta.betting.application.dto.BetPageRequest;
import com.suaposta.betting.domain.model.Bet;
import java.util.Optional;
import java.util.UUID;

public interface BetRepository {

    Bet save(Bet bet);

    Optional<Bet> findByIdAndUserId(UUID betId, UUID userId);

    BetPage findAllByUserId(UUID userId, BetFilters filters, BetPageRequest pagination);
}
