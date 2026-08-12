package com.suaposta.auth.presentation.dto;

import java.util.UUID;

public record AuthenticatedUserResponse(UUID id, String name, String email) {
}
