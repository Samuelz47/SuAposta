package com.suaposta.betting.application.exception;

public final class BetNotFoundException extends RuntimeException {

    public BetNotFoundException() {
        super("Bet not found");
    }
}
