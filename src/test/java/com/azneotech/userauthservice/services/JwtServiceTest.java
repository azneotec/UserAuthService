package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.configs.JwtProperties;
import com.azneotech.userauthservice.models.Role;
import com.azneotech.userauthservice.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // 32 zero bytes, Base64-encoded: long enough for HS256, obviously not a real secret.
    private static final String SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String OTHER_SECRET = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    private static JwtProperties props(String secret, Duration expiration, String issuer) {
        return new JwtProperties(secret, expiration, issuer);
    }

    private static Clock fixed(Instant at) {
        return Clock.fixed(at, ZoneOffset.UTC);
    }

    private static User user() {
        User user = User.builder().id(42L).email("alice@example.com").build();
        user.getRoles().add(Role.builder().value("ROLE_USER").build());
        user.getRoles().add(Role.builder().value("ROLE_ADMIN").build());
        return user;
    }

    @Test
    void issueThenParse_roundTripsStandardAndCustomClaims() {
        JwtService service = new JwtService(props(SECRET, Duration.ofHours(24), "user-auth-service"), fixed(NOW));

        IssuedToken issued = service.issue(user());
        Claims claims = service.parse(issued.token());

        assertThat(issued.issuedAt()).isEqualTo(NOW);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuer()).isEqualTo("user-auth-service");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo("alice@example.com");
        assertThat(claims.get(JwtService.CLAIM_ROLES, List.class)).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void issue_producesDistinctTokensForBackToBackLogins() {
        JwtService service = new JwtService(props(SECRET, Duration.ofHours(1), "iss"), fixed(NOW));

        String first = service.issue(user()).token();
        String second = service.issue(user()).token();

        // Same user, same second: only jti differs, and that is enough for the unique token_hash index.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void parse_throwsExpiredJwtException_whenClockPassesExpiry() {
        JwtProperties properties = props(SECRET, Duration.ofMinutes(5), "iss");
        String token = new JwtService(properties, fixed(NOW)).issue(user()).token();

        JwtService later = new JwtService(properties, fixed(NOW.plus(Duration.ofMinutes(6))));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parse_throwsSignatureException_whenSignedWithAnotherKey() {
        String token = new JwtService(props(OTHER_SECRET, Duration.ofHours(1), "iss"), fixed(NOW)).issue(user()).token();
        JwtService service = new JwtService(props(SECRET, Duration.ofHours(1), "iss"), fixed(NOW));

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    void parse_throwsIncorrectClaimException_whenIssuerDiffers() {
        String token = new JwtService(props(SECRET, Duration.ofHours(1), "someone-else"), fixed(NOW)).issue(user()).token();
        JwtService service = new JwtService(props(SECRET, Duration.ofHours(1), "user-auth-service"), fixed(NOW));

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void parse_throwsOnGarbageInput() {
        JwtService service = new JwtService(props(SECRET, Duration.ofHours(1), "iss"), fixed(NOW));

        assertThatThrownBy(() -> service.parse("not.a.jwt")).isInstanceOf(MalformedJwtException.class);
        assertThatThrownBy(() -> service.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsWeakSecret() {
        String shortSecret = "c2hvcnQ="; // "short"

        assertThatThrownBy(() -> new JwtService(props(shortSecret, Duration.ofHours(1), "iss"), fixed(NOW)))
                .isInstanceOf(WeakKeyException.class);
    }

}
