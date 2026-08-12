package com.suaposta.auth.domain.model;

import com.suaposta.auth.domain.exception.InvalidUserException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Pure domain representation of an Auth Service user. */
public record User(
        UUID id,
        String name,
        String email,
        String passwordHash,
        Instant createdAt,
        Instant updatedAt) {

    public User {
        if (id == null) {
            throw new InvalidUserException("User id is required");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidUserException("User name is required");
        }
        if (email == null || email.isBlank()) {
            throw new InvalidUserException("User email is required");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new InvalidUserException("User password hash is required");
        }
        Objects.requireNonNull(createdAt, "User createdAt is required");
        Objects.requireNonNull(updatedAt, "User updatedAt is required");
    }
}
