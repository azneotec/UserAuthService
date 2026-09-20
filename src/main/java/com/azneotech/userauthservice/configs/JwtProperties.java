package com.azneotech.userauthservice.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * JWT settings bound from {@code jwt.*}. Enabled from {@link AppConfig}, not the main application
 * class, so {@code @WebMvcTest} slices never try to bind {@code jwt.secret}.
 *
 * @param secret     Base64-encoded HMAC key; must decode to at least 32 bytes (HS256).
 *                   Generate one with {@code openssl rand -base64 32}.
 * @param expiration how long an issued token (and its session) stays valid.
 * @param issuer     value written to the {@code iss} claim and required on parse.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("24h") Duration expiration,
        @DefaultValue("user-auth-service") String issuer
) {
}
