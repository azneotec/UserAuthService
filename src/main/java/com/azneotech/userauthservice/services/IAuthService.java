package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.models.User;

public interface IAuthService {

    User signup(String name, String email, String phoneNumber, String password);

    LoginResult login(String email, String password);

    /** Never throws: any malformed, forged, expired or revoked token yields {@link TokenValidationResult#invalid()}. */
    TokenValidationResult validateToken(String token);

    /** Revokes the given session. Idempotent: unknown or already-inactive sessions are a no-op. */
    void logout(Long sessionId);

}
