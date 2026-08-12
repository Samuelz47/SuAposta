package com.suaposta.auth.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record RegisterUserResponse(UUID id, String name, String email, Instant createdAt) {
}
