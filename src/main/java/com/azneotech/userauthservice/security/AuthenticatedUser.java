package com.azneotech.userauthservice.security;

/**
 * Principal placed in the {@code SecurityContext} by {@link JwtAuthenticationFilter}.
 * {@code sessionId} lets logout revoke the calling session without re-parsing the header.
 */
public record AuthenticatedUser(Long userId, String email, Long sessionId) {
}
