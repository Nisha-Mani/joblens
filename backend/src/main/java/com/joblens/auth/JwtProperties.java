package com.joblens.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expiryMinutes, String issuer) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes().length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES
                    + " bytes long (generate one with: openssl rand -base64 48)");
        }
        if (expiryMinutes <= 0) {
            throw new IllegalStateException("app.jwt.expiry-minutes must be positive");
        }
    }
}
