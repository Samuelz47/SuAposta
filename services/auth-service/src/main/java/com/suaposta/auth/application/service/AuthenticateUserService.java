package com.suaposta.auth.application.service;

import com.suaposta.auth.application.condition.AuthLoginConfiguredCondition;
import com.suaposta.auth.application.dto.AuthenticatedUser;
import com.suaposta.auth.application.dto.LoginResult;
import com.suaposta.auth.application.dto.LoginUserCommand;
import com.suaposta.auth.application.port.AccessTokenGenerator;
import com.suaposta.auth.domain.exception.InvalidCredentialsException;
import com.suaposta.auth.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Conditional(AuthLoginConfiguredCondition.class)
public class AuthenticateUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenGenerator accessTokenGenerator;
    private final String dummyPasswordHash;

    public AuthenticateUserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenGenerator accessTokenGenerator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenGenerator = accessTokenGenerator;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResult authenticate(LoginUserCommand command) {
        var normalizedEmail = normalizeEmail(command.email());
        var user = userRepository.findByEmail(normalizedEmail);
        if (user.isEmpty()) {
            passwordEncoder.matches(command.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        var authenticatedUser = user.get();
        if (!passwordEncoder.matches(command.password(), authenticatedUser.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        var issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var accessToken = accessTokenGenerator.generate(authenticatedUser.id(), issuedAt);
        var responseUser = new AuthenticatedUser(
                authenticatedUser.id(), authenticatedUser.name(), authenticatedUser.email());
        return new LoginResult(accessToken, AccessTokenGenerator.EXPIRES_IN_SECONDS, responseUser);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
