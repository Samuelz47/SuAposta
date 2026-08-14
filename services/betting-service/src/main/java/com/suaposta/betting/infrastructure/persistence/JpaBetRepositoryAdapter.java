package com.suaposta.betting.infrastructure.persistence;

import com.suaposta.betting.application.dto.BetFilters;
import com.suaposta.betting.application.dto.BetPage;
import com.suaposta.betting.application.dto.BetPageRequest;
import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.infrastructure.config.BettingPersistenceConfiguredCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(BettingPersistenceConfiguredCondition.class)
public class JpaBetRepositoryAdapter implements BetRepository {

    private final SpringDataBetRepository repository;

    public JpaBetRepositoryAdapter(SpringDataBetRepository repository) {
        this.repository = repository;
    }

    @Override
    public Bet save(Bet bet) {
        return repository.saveAndFlush(new BetEntity(bet)).toDomain();
    }

    @Override
    public Optional<Bet> findByIdAndUserId(UUID betId, UUID userId) {
        return repository.findByIdAndUserId(betId, userId).map(BetEntity::toDomain);
    }

    @Override
    public BetPage findAllByUserId(UUID userId, BetFilters filters, BetPageRequest pagination) {
        Specification<BetEntity> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("userId"), userId);

        specification = andEqual(specification, "sport", filters.sport());
        specification = andEqual(specification, "league", filters.league());
        specification = andEqual(specification, "market", filters.market());
        specification = andEqual(specification, "status", filters.status());
        if (filters.team() != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.equal(root.get("homeTeam"), filters.team()),
                    criteriaBuilder.equal(root.get("awayTeam"), filters.team())));
        }
        specification = andGreaterThanOrEqualTo(
                specification, "placedAt", filters.startDate(), Instant.class);
        specification = andLessThanOrEqualTo(
                specification, "placedAt", filters.endDate(), Instant.class);
        specification = andGreaterThanOrEqualTo(
                specification, "odds", filters.minOdds(), BigDecimal.class);
        specification = andLessThanOrEqualTo(
                specification, "odds", filters.maxOdds(), BigDecimal.class);
        specification = andGreaterThanOrEqualTo(
                specification, "stake", filters.minStake(), BigDecimal.class);
        specification = andLessThanOrEqualTo(
                specification, "stake", filters.maxStake(), BigDecimal.class);

        var result = repository.findAll(
                specification, PageRequest.of(pagination.page(), pagination.size()));
        return new BetPage(
                result.getContent().stream().map(BetEntity::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private static Specification<BetEntity> andEqual(
            Specification<BetEntity> specification,
            String field,
            Object value) {
        if (value == null) {
            return specification;
        }
        return specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get(field), value));
    }

    private static <T extends Comparable<? super T>> Specification<BetEntity> andGreaterThanOrEqualTo(
            Specification<BetEntity> specification,
            String field,
            T value,
            Class<T> type) {
        if (value == null) {
            return specification;
        }
        return specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get(field).as(type), value));
    }

    private static <T extends Comparable<? super T>> Specification<BetEntity> andLessThanOrEqualTo(
            Specification<BetEntity> specification,
            String field,
            T value,
            Class<T> type) {
        if (value == null) {
            return specification;
        }
        return specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(root.get(field).as(type), value));
    }
}
