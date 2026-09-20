package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.configs.PasswordResetProperties;
import com.azneotech.userauthservice.exceptions.InvalidPasswordResetTokenException;
import com.azneotech.userauthservice.models.PasswordResetToken;
import com.azneotech.userauthservice.models.Status;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.repos.PasswordResetTokenRepo;
import com.azneotech.userauthservice.repos.SessionRepo;
import com.azneotech.userauthservice.repos.UserRepo;
import com.azneotech.userauthservice.utils.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PasswordResetProperties PROPERTIES =
            new PasswordResetProperties(Duration.ofMinutes(15), "http://localhost:3000/reset-password?token=");

    @Mock UserRepo userRepo;
    @Mock PasswordResetTokenRepo tokenRepo;
    @Mock SessionRepo sessionRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock IEmailService emailService;

    PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepo, tokenRepo, sessionRepo, passwordEncoder, emailService, PROPERTIES, CLOCK);
    }

    private static User alice() {
        return User.builder().id(7L).email("alice@example.com").password("old-hash").build();
    }

    @Nested
    class RequestReset {

        @Test
        void storesHashedTokenAndEmailsRawToken() {
            User user = alice();
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

            service.requestReset("  Alice@Example.com ");

            ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendPasswordResetEmail(eq("alice@example.com"), rawTokenCaptor.capture());
            String rawToken = rawTokenCaptor.getValue();
            assertThat(rawToken).isNotBlank().doesNotContain("=", "+", "/"); // URL-safe, unpadded

            ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepo).save(tokenCaptor.capture());
            PasswordResetToken saved = tokenCaptor.getValue();
            assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(rawToken));
            assertThat(saved.getUser()).isSameAs(user);
            assertThat(saved.getExpiresAt()).isEqualTo(NOW_LOCAL.plusMinutes(15));
            assertThat(saved.getUsedAt()).isNull();
        }

        @Test
        void unknownEmailIsSilentlyIgnored() {
            when(userRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            service.requestReset("nobody@example.com");

            verifyNoInteractions(tokenRepo, emailService);
        }

        @Test
        void inactiveUserIsTreatedAsUnknown() {
            User inactive = alice();
            inactive.setStatus(Status.INACTIVE);
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(inactive));

            service.requestReset("alice@example.com");

            verifyNoInteractions(tokenRepo, emailService);
        }

        @Test
        void eachRequestProducesADifferentToken() {
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(alice()));

            service.requestReset("alice@example.com");
            service.requestReset("alice@example.com");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(emailService, org.mockito.Mockito.times(2)).sendPasswordResetEmail(anyString(), captor.capture());
            assertThat(captor.getAllValues()).doesNotHaveDuplicates();
        }
    }

    @Nested
    class ResetPassword {

        private static final String RAW = "raw-reset-token";

        private PasswordResetToken liveToken(User user) {
            return PasswordResetToken.builder()
                    .id(1L)
                    .tokenHash(TokenHasher.sha256Hex(RAW))
                    .user(user)
                    .expiresAt(NOW_LOCAL.plusMinutes(5))
                    .build();
        }

        @Test
        void setsNewPasswordConsumesTokenAndRevokesSessions() {
            User user = alice();
            PasswordResetToken token = liveToken(user);
            when(tokenRepo.findByTokenHash(TokenHasher.sha256Hex(RAW))).thenReturn(Optional.of(token));
            when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");
            when(sessionRepo.deactivateAllActiveForUser(7L)).thenReturn(2);

            service.resetPassword(RAW, "new-secret");

            assertThat(user.getPassword()).isEqualTo("new-hash");
            assertThat(token.getUsedAt()).isEqualTo(NOW_LOCAL);
            verify(userRepo).save(user);
            verify(tokenRepo).save(token);
            verify(sessionRepo).deactivateAllActiveForUser(7L);
        }

        @Test
        void rejectsUnknownToken() {
            when(tokenRepo.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resetPassword("nope", "new-secret"))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);
            verifyNoInteractions(passwordEncoder, sessionRepo);
            verify(userRepo, never()).save(any());
        }

        @Test
        void rejectsAlreadyUsedToken() {
            User user = alice();
            PasswordResetToken token = liveToken(user);
            token.setUsedAt(NOW_LOCAL.minusMinutes(1));
            when(tokenRepo.findByTokenHash(TokenHasher.sha256Hex(RAW))).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.resetPassword(RAW, "new-secret"))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);
            assertThat(user.getPassword()).isEqualTo("old-hash");
            verifyNoInteractions(passwordEncoder, sessionRepo);
        }

        @Test
        void rejectsExpiredToken() {
            User user = alice();
            PasswordResetToken token = liveToken(user);
            token.setExpiresAt(NOW_LOCAL.minusSeconds(1));
            when(tokenRepo.findByTokenHash(TokenHasher.sha256Hex(RAW))).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.resetPassword(RAW, "new-secret"))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);
            assertThat(user.getPassword()).isEqualTo("old-hash");
            assertThat(token.getUsedAt()).isNull();
            verifyNoInteractions(passwordEncoder, sessionRepo);
        }
    }

}
