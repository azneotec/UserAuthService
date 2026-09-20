package com.azneotech.userauthservice.services;

import java.time.Instant;
import java.util.List;

/**
 * Result of introspecting a bearer token. When {@code valid} is false every other field is null/empty.
 * {@code sessionId} is for internal use (logout); it is not exposed to callers of the validate endpoint.
 */
public record TokenValidationResult(
        boolean valid,
        Long userId,
        String email,
        List<String> roles,
        Instant expiresAt,
        Long sessionId
) {

    private static final TokenValidationResult INVALID =
            new TokenValidationResult(false, null, null, List.of(), null, null);

    public static TokenValidationResult invalid() {
        return INVALID;
    }

}
