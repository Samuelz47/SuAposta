package com.suaposta.auth.domain.repository;

import com.suaposta.auth.domain.model.User;
import java.util.Optional;
import java.util.UUID;

/** Persistence port owned by the Auth domain. */
public interface UserRepository {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID id);

    User save(User user);
}
