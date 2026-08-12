package com.suaposta.auth.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Registration result containing only fields safe for the API boundary. */
public record RegisteredUser(UUID id, String name, String email, Instant createdAt) {
}
