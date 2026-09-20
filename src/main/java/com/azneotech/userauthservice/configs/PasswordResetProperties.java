package com.azneotech.userauthservice.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Password-reset settings bound from {@code password-reset.*}.
 *
 * @param expiration  how long a reset token can be used after it is issued.
 * @param linkBaseUrl prefix the raw token is appended to when building the link sent to the user.
 */
@ConfigurationProperties(prefix = "password-reset")
public record PasswordResetProperties(
        @DefaultValue("15m") Duration expiration,
        @DefaultValue("http://localhost:3000/reset-password?token=") String linkBaseUrl
) {
}
