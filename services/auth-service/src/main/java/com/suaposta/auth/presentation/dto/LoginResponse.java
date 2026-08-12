package com.suaposta.auth.presentation.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AuthenticatedUserResponse user) {
}
