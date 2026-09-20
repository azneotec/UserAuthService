package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

public interface IJwtService {

    /** Signs a new token for {@code user} carrying standard claims plus {@code email} and {@code roles}. */
    IssuedToken issue(User user);

    /**
     * Verifies signature, expiry and issuer, and returns the claims.
     *
     * @throws JwtException             if the token is malformed, tampered with, expired, or from another issuer
     *                                  (see {@link io.jsonwebtoken.ExpiredJwtException} for the expired case)
     * @throws IllegalArgumentException if {@code token} is null or blank
     */
    Claims parse(String token);

}
