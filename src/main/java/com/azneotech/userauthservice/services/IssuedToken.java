package com.azneotech.userauthservice.services;

import java.time.Instant;

/** A freshly signed JWT together with the timestamps baked into its claims. */
public record IssuedToken(String token, Instant issuedAt, Instant expiresAt) {
}
