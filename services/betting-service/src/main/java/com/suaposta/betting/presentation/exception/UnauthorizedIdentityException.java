package com.suaposta.betting.presentation.exception;

public final class UnauthorizedIdentityException extends RuntimeException {

    public UnauthorizedIdentityException() {
        super("Authenticated identity is required");
    }
}
