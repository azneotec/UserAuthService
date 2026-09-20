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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class PasswordResetService implements IPasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserRepo userRepo;
    private final PasswordResetTokenRepo tokenRepo;
    private final SessionRepo sessionRepo;
    private final PasswordEncoder passwordEncoder;
    private final IEmailService emailService;
    private final PasswordResetProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepo userRepo,
            PasswordResetTokenRepo tokenRepo,
            SessionRepo sessionRepo,
            PasswordEncoder passwordEncoder,
            IEmailService emailService,
            PasswordResetProperties properties,
            Clock clock
    ) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.sessionRepo = sessionRepo;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void requestReset(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> userOptional = userRepo.findByEmail(normalized)
                .filter(user -> user.getStatus() == Status.ACTIVE);
        if (userOptional.isEmpty()) {
            // Silently succeed: the controller returns the same response either way.
            log.debug("Password reset requested for unknown or inactive email");
            return;
        }
        User user = userOptional.get();

        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(TokenHasher.sha256Hex(rawToken))
                .user(user)
                .expiresAt(LocalDateTime.now(clock).plus(properties.expiration()))
                .build();
        tokenRepo.save(token);

        emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);

        PasswordResetToken token = tokenRepo.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Unknown password reset token"));
        if (token.getUsedAt() != null) {
            throw new InvalidPasswordResetTokenException("Password reset token has already been used");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new InvalidPasswordResetTokenException("Password reset token has expired");
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);

        token.setUsedAt(now);
        tokenRepo.save(token);

        // Anyone holding an old token for this account is signed out.
        int revoked = sessionRepo.deactivateAllActiveForUser(user.getId());
        log.info("Password reset for user {}; revoked {} session(s)", user.getId(), revoked);
    }

}
