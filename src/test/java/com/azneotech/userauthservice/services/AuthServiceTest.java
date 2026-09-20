package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.exceptions.PasswordMismatchException;
import com.azneotech.userauthservice.exceptions.UserAlreadyExistsException;
import com.azneotech.userauthservice.exceptions.UserNotRegisteredException;
import com.azneotech.userauthservice.models.Role;
import com.azneotech.userauthservice.models.Status;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.models.UserSession;
import com.azneotech.userauthservice.repos.RoleRepo;
import com.azneotech.userauthservice.repos.SessionRepo;
import com.azneotech.userauthservice.repos.UserRepo;
import com.azneotech.userauthservice.utils.TokenHasher;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TOKEN = "header.payload.signature";

    @Mock UserRepo userRepo;
    @Mock RoleRepo roleRepo;
    @Mock SessionRepo sessionRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock IJwtService jwtService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepo, roleRepo, sessionRepo, passwordEncoder, jwtService, CLOCK);
    }

    private static Role role(String value) {
        return Role.builder().value(value).build();
    }

    private static User activeUser() {
        User user = User.builder().id(7L).name("Alice").email("alice@example.com").password("$2a$hash").build();
        user.getRoles().add(role("ROLE_USER"));
        return user;
    }

    private static Claims claimsFor(User user, Instant expiresAt) {
        return Jwts.claims()
                .subject(String.valueOf(user.getId()))
                .expiration(Date.from(expiresAt))
                .add(JwtService.CLAIM_EMAIL, user.getEmail())
                .add(JwtService.CLAIM_ROLES, List.of("ROLE_USER"))
                .build();
    }

    @Nested
    class Signup {

        @Test
        void savesUserWithEncodedPasswordDefaultRoleAndNormalizedEmail() {
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.empty());
            when(userRepo.findByPhoneNumber("+15551234567")).thenReturn(Optional.empty());
            when(roleRepo.findByValue("ROLE_USER")).thenReturn(Optional.of(role("ROLE_USER")));
            when(passwordEncoder.encode("secret123")).thenReturn("encoded");
            when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            User saved = authService.signup("Alice", "  Alice@Example.COM ", "+15551234567", "secret123");

            assertThat(saved.getEmail()).isEqualTo("alice@example.com");
            assertThat(saved.getPassword()).isEqualTo("encoded");
            assertThat(saved.getPhoneNumber()).isEqualTo("+15551234567");
            assertThat(saved.getRoles()).extracting(Role::getValue).containsExactly("ROLE_USER");
        }

        @Test
        void rejectsDuplicateEmail() {
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser()));

            assertThatThrownBy(() -> authService.signup("Alice", "alice@example.com", null, "secret123"))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("email");
            verify(userRepo, never()).save(any());
        }

        @Test
        void rejectsDuplicatePhoneNumber() {
            when(userRepo.findByEmail("bob@example.com")).thenReturn(Optional.empty());
            when(userRepo.findByPhoneNumber("+15551234567")).thenReturn(Optional.of(activeUser()));

            assertThatThrownBy(() -> authService.signup("Bob", "bob@example.com", "+15551234567", "secret123"))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("phone");
            verify(userRepo, never()).save(any());
        }

        @Test
        void skipsPhoneLookupWhenPhoneIsAbsent() {
            when(userRepo.findByEmail("bob@example.com")).thenReturn(Optional.empty());
            when(roleRepo.findByValue("ROLE_USER")).thenReturn(Optional.of(role("ROLE_USER")));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            authService.signup("Bob", "bob@example.com", "  ", "secret123");

            verify(userRepo, never()).findByPhoneNumber(anyString());
        }

        @Test
        void failsLoudlyWhenDefaultRoleIsMissing() {
            when(userRepo.findByEmail("bob@example.com")).thenReturn(Optional.empty());
            when(roleRepo.findByValue("ROLE_USER")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.signup("Bob", "bob@example.com", null, "secret123"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ROLE_USER");
        }

        @Test
        void translatesUniqueConstraintRaceIntoUserAlreadyExists() {
            when(userRepo.findByEmail("bob@example.com")).thenReturn(Optional.empty());
            when(roleRepo.findByValue("ROLE_USER")).thenReturn(Optional.of(role("ROLE_USER")));
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepo.save(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_user_email"));

            assertThatThrownBy(() -> authService.signup("Bob", "bob@example.com", null, "secret123"))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }
    }

    @Nested
    class Login {

        @Test
        void issuesTokenAndStoresHashedSession() {
            User user = activeUser();
            Instant expiresAt = NOW.plus(Duration.ofHours(24));
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("secret123", "$2a$hash")).thenReturn(true);
            when(jwtService.issue(user)).thenReturn(new IssuedToken(TOKEN, NOW, expiresAt));

            LoginResult result = authService.login("Alice@Example.com", "secret123");

            assertThat(result.user()).isSameAs(user);
            assertThat(result.token()).isEqualTo(TOKEN);
            assertThat(result.expiresAt()).isEqualTo(expiresAt);

            ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
            verify(sessionRepo).save(captor.capture());
            UserSession session = captor.getValue();
            assertThat(session.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(TOKEN));
            assertThat(session.getExpiresAt()).isEqualTo(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
            assertThat(session.getUser()).isSameAs(user);
            assertThat(session.getStatus()).isEqualTo(Status.ACTIVE);
        }

        @Test
        void rejectsUnknownEmail() {
            when(userRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("nobody@example.com", "secret123"))
                    .isInstanceOf(UserNotRegisteredException.class);
            verifyNoInteractions(jwtService, sessionRepo);
        }

        @Test
        void rejectsWrongPassword() {
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser()));
            when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.login("alice@example.com", "wrong"))
                    .isInstanceOf(PasswordMismatchException.class);
            verifyNoInteractions(jwtService, sessionRepo);
        }

        @Test
        void rejectsInactiveUserBeforeCheckingPassword() {
            User inactive = activeUser();
            inactive.setStatus(Status.INACTIVE);
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> authService.login("alice@example.com", "secret123"))
                    .isInstanceOf(UserNotRegisteredException.class);
            verifyNoInteractions(passwordEncoder, jwtService, sessionRepo);
        }
    }

    @Nested
    class ValidateToken {

        private UserSession activeSession(User user) {
            return UserSession.builder()
                    .id(99L)
                    .tokenHash(TokenHasher.sha256Hex(TOKEN))
                    .expiresAt(LocalDateTime.ofInstant(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC))
                    .user(user)
                    .build();
        }

        @Test
        void returnsIdentityForValidTokenWithActiveSession() {
            User user = activeUser();
            Instant expiresAt = NOW.plus(Duration.ofHours(1));
            when(jwtService.parse(TOKEN)).thenReturn(claimsFor(user, expiresAt));
            when(sessionRepo.findByTokenHash(TokenHasher.sha256Hex(TOKEN))).thenReturn(Optional.of(activeSession(user)));

            TokenValidationResult result = authService.validateToken(TOKEN);

            assertThat(result.valid()).isTrue();
            assertThat(result.userId()).isEqualTo(7L);
            assertThat(result.email()).isEqualTo("alice@example.com");
            assertThat(result.roles()).containsExactly("ROLE_USER");
            assertThat(result.expiresAt()).isEqualTo(expiresAt);
            assertThat(result.sessionId()).isEqualTo(99L);
        }

        @Test
        void expiredTokenSoftDeletesSessionAndIsInvalid() {
            UserSession session = activeSession(activeUser());
            when(jwtService.parse(TOKEN)).thenThrow(new ExpiredJwtException(null, null, "expired"));
            when(sessionRepo.findByTokenHash(TokenHasher.sha256Hex(TOKEN))).thenReturn(Optional.of(session));

            TokenValidationResult result = authService.validateToken(TOKEN);

            assertThat(result.valid()).isFalse();
            assertThat(session.getStatus()).isEqualTo(Status.INACTIVE);
            verify(sessionRepo).save(session);
            verify(sessionRepo, never()).deleteById(any());
        }

        @Test
        void malformedTokenIsInvalidWithoutTouchingTheDatabase() {
            when(jwtService.parse("garbage")).thenThrow(new MalformedJwtException("bad"));

            TokenValidationResult result = authService.validateToken("garbage");

            assertThat(result.valid()).isFalse();
            verifyNoInteractions(sessionRepo);
        }

        @Test
        void blankTokenIsInvalid() {
            when(jwtService.parse("")).thenThrow(new IllegalArgumentException("blank"));

            assertThat(authService.validateToken("").valid()).isFalse();
            verifyNoInteractions(sessionRepo);
        }

        @Test
        void validSignatureButNoSessionIsInvalid() {
            when(jwtService.parse(TOKEN)).thenReturn(claimsFor(activeUser(), NOW.plus(Duration.ofHours(1))));
            when(sessionRepo.findByTokenHash(TokenHasher.sha256Hex(TOKEN))).thenReturn(Optional.empty());

            assertThat(authService.validateToken(TOKEN).valid()).isFalse();
        }

        @Test
        void revokedSessionIsInvalid() {
            UserSession session = activeSession(activeUser());
            session.setStatus(Status.INACTIVE);
            when(jwtService.parse(TOKEN)).thenReturn(claimsFor(activeUser(), NOW.plus(Duration.ofHours(1))));
            when(sessionRepo.findByTokenHash(TokenHasher.sha256Hex(TOKEN))).thenReturn(Optional.of(session));

            assertThat(authService.validateToken(TOKEN).valid()).isFalse();
        }

        @Test
        void inactiveUserIsInvalidEvenWithLiveSession() {
            User user = activeUser();
            user.setStatus(Status.INACTIVE);
            when(jwtService.parse(TOKEN)).thenReturn(claimsFor(user, NOW.plus(Duration.ofHours(1))));
            when(sessionRepo.findByTokenHash(TokenHasher.sha256Hex(TOKEN))).thenReturn(Optional.of(activeSession(user)));

            assertThat(authService.validateToken(TOKEN).valid()).isFalse();
        }
    }

    @Nested
    class Logout {

        @Test
        void marksActiveSessionInactive() {
            UserSession session = UserSession.builder().id(5L).user(activeUser()).build();
            when(sessionRepo.findById(5L)).thenReturn(Optional.of(session));

            authService.logout(5L);

            assertThat(session.getStatus()).isEqualTo(Status.INACTIVE);
            verify(sessionRepo).save(session);
        }

        @Test
        void isNoOpForUnknownOrAlreadyInactiveSession() {
            UserSession inactive = UserSession.builder().id(6L).status(Status.INACTIVE).build();
            when(sessionRepo.findById(5L)).thenReturn(Optional.empty());
            when(sessionRepo.findById(6L)).thenReturn(Optional.of(inactive));

            authService.logout(5L);
            authService.logout(6L);

            verify(sessionRepo, never()).save(any());
        }
    }

}
