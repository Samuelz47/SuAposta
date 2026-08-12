package com.suaposta.auth.domain.repository;

import com.suaposta.auth.domain.model.User;

/** Persistence port owned by the Auth domain. */
public interface UserRepository {

    boolean existsByEmail(String email);

    User save(User user);
}
