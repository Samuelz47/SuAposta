package com.suaposta.auth.application.service;

import com.suaposta.auth.application.condition.AuthPersistenceConfiguredCondition;
import com.suaposta.auth.application.dto.AuthenticatedUser;
import com.suaposta.auth.domain.exception.InvalidCredentialsException;
import com.suaposta.auth.domain.repository.UserRepository;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Conditional(AuthPersistenceConfiguredCondition.class)
public class IdentifyCurrentUserService {

    private final UserRepository userRepository;

    public IdentifyCurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser identify(String authenticatedUserId) {
        var userId = parseUserId(authenticatedUserId);
        var user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        return new AuthenticatedUser(user.id(), user.name(), user.email());
    }

    private static UUID parseUserId(String authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new InvalidCredentialsException();
        }

        try {
            var userId = UUID.fromString(authenticatedUserId);
            if (!userId.toString().equalsIgnoreCase(authenticatedUserId)) {
                throw new InvalidCredentialsException();
            }
            return userId;
        } catch (IllegalArgumentException exception) {
            throw new InvalidCredentialsException();
        }
    }
}
