package com.suaposta.auth.infrastructure.security;

import com.suaposta.auth.application.port.AccessTokenGenerator;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class Hs256JwtAccessTokenGenerator implements AccessTokenGenerator {

    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] signingSecret;

    public Hs256JwtAccessTokenGenerator(String signingSecret) {
        if (!StringUtils.hasText(signingSecret)) {
            throw new IllegalArgumentException("The JWT signing secret must be configured");
        }
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String generate(UUID subject, Instant issuedAt) {
        var issuedAtSeconds = issuedAt.getEpochSecond();
        var expirationSeconds = issuedAtSeconds + EXPIRES_IN_SECONDS;
        var encodedHeader = encode(HEADER);
        var encodedPayload = encode("{\"sub\":\""
                + subject
                + "\",\"iat\":"
                + issuedAtSeconds
                + ",\"exp\":"
                + expirationSeconds
                + "}");
        var signingInput = encodedHeader + "." + encodedPayload;

        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            var signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not generate access token", exception);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
