package com.suaposta.auth.application.dto;

import java.util.UUID;

/** Authenticated user data safe for the API response. */
public record AuthenticatedUser(UUID id, String name, String email) {
}
