package com.suaposta.auth.application.service;

import com.suaposta.auth.application.dto.RegisterUserCommand;
import com.suaposta.auth.application.dto.RegisteredUser;
import com.suaposta.auth.application.condition.AuthPersistenceConfiguredCondition;
import com.suaposta.auth.domain.exception.DuplicateEmailException;
import com.suaposta.auth.domain.model.User;
import com.suaposta.auth.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Conditional(AuthPersistenceConfiguredCondition.class)
public class RegisterUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisteredUser register(RegisterUserCommand command) {
        var normalizedEmail = normalizeEmail(command.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        var now = Instant.now();
        var user = new User(
                UUID.randomUUID(),
                command.name(),
                normalizedEmail,
                passwordEncoder.encode(command.password()),
                now,
                now);
        var savedUser = userRepository.save(user);

        return new RegisteredUser(
                savedUser.id(),
                savedUser.name(),
                savedUser.email(),
                savedUser.createdAt());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
