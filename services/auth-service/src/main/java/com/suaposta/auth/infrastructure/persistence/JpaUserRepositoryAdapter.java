package com.suaposta.auth.infrastructure.persistence;

import com.suaposta.auth.application.condition.AuthPersistenceConfiguredCondition;
import com.suaposta.auth.domain.exception.DuplicateEmailException;
import com.suaposta.auth.domain.model.User;
import com.suaposta.auth.domain.repository.UserRepository;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(AuthPersistenceConfiguredCondition.class)
public class JpaUserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository repository;

    public JpaUserRepositoryAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public User save(User user) {
        try {
            return repository.saveAndFlush(new UserEntity(user)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }

    private static boolean isUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
