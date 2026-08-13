package com.suaposta.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.suaposta.auth.application.dto.AuthenticatedUser;
import com.suaposta.auth.application.service.IdentifyCurrentUserService;
import com.suaposta.auth.domain.exception.InvalidCredentialsException;
import com.suaposta.auth.domain.model.User;
import com.suaposta.auth.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentifyCurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    private IdentifyCurrentUserService service;

    @BeforeEach
    void setUp() {
        service = new IdentifyCurrentUserService(userRepository);
    }

    @Test
    void should_return_safe_authenticated_user_when_x_user_id_matches_existing_user() {
        var userId = UUID.randomUUID();
        var user = user(userId, "Samuel Gomes", "samuel@example.com", "sensitive-password-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var result = service.identify(userId.toString());

        assertThat(result).isEqualTo(new AuthenticatedUser(userId, user.name(), user.email()));
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.name()).isEqualTo(user.name());
        assertThat(result.email()).isEqualTo(user.email());
        assertThat(result.toString()).doesNotContain(user.passwordHash());
        verify(userRepository).findById(userId);
    }

    @Test
    void should_throw_invalid_credentials_when_x_user_id_has_no_corresponding_user() {
        var userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.identify(userId.toString()))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository).findById(userId);
    }

    @Test
    void should_throw_invalid_credentials_without_querying_repository_when_x_user_id_is_missing() {
        assertThatThrownBy(() -> service.identify(null))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void should_throw_invalid_credentials_without_querying_repository_when_x_user_id_is_malformed() {
        assertThatThrownBy(() -> service.identify("not-a-user-uuid"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(userRepository);
    }

    private static User user(UUID id, String name, String email, String passwordHash) {
        var now = Instant.now();
        return new User(id, name, email, passwordHash, now, now);
    }
}
