package com.suaposta.betting.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataBetRepository
        extends JpaRepository<BetEntity, UUID>, JpaSpecificationExecutor<BetEntity> {

    Optional<BetEntity> findByIdAndUserId(UUID id, UUID userId);
}
