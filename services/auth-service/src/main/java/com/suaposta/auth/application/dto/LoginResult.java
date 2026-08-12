package com.suaposta.auth.application.dto;

public record LoginResult(
        String accessToken,
        long expiresIn,
        AuthenticatedUser user) {
}
