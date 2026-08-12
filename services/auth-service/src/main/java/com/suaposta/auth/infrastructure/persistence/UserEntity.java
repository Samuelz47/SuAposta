package com.suaposta.auth.infrastructure.persistence;

import com.suaposta.auth.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    UserEntity(User user) {
        this.id = user.id();
        this.name = user.name();
        this.email = user.email();
        this.passwordHash = user.passwordHash();
        this.createdAt = user.createdAt();
        this.updatedAt = user.updatedAt();
    }

    User toDomain() {
        return new User(id, name, email, passwordHash, createdAt, updatedAt);
    }
}
