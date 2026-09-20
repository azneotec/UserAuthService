package com.azneotech.userauthservice.security;

import com.azneotech.userauthservice.services.IAuthService;
import com.azneotech.userauthservice.services.TokenValidationResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <jwt>}, validates it through {@link IAuthService#validateToken}
 * (signature, expiry, and that the session is still ACTIVE) and, if valid, populates the
 * {@code SecurityContext} with an {@link AuthenticatedUser} principal and the token's roles.
 * <p>
 * Missing or invalid tokens are not rejected here; the request continues anonymously and the
 * authorization rules decide (protected endpoints then get a 401 from the entry point).
 * <p>
 * Deliberately not a {@code @Component}: it is instantiated inside the {@code SecurityFilterChain}
 * bean so Boot doesn't also register it as a plain servlet filter and run it twice.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final IAuthService authService;

    public JwtAuthenticationFilter(IAuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean hasBearer = header != null
                && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());

        if (hasBearer && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            TokenValidationResult result = authService.validateToken(token);
            if (result.valid()) {
                List<GrantedAuthority> authorities = result.roles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .map(GrantedAuthority.class::cast)
                        .toList();
                AuthenticatedUser principal = new AuthenticatedUser(result.userId(), result.email(), result.sessionId());
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        chain.doFilter(request, response);
    }

}
