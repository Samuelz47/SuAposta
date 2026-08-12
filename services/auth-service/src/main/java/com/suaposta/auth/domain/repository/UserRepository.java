package com.suaposta.auth.domain.repository;

import com.suaposta.auth.domain.model.User;
import java.util.Optional;

/** Persistence port owned by the Auth domain. */
public interface UserRepository {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    User save(User user);
}
