package com.suaposta.auth.application.port;

import java.time.Instant;
import java.util.UUID;

public interface AccessTokenGenerator {

    long EXPIRES_IN_SECONDS = 3600L;

    String generate(UUID subject, Instant issuedAt);
}
