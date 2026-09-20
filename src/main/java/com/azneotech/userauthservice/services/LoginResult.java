package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.models.User;

import java.time.Instant;

/** Outcome of a successful login: the authenticated user and the token issued for them. */
public record LoginResult(User user, String token, Instant expiresAt) {
}
