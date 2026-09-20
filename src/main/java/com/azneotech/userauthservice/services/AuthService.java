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
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class AuthService implements IAuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final SessionRepo sessionRepo;
    private final PasswordEncoder passwordEncoder;
    private final IJwtService jwtService;
    private final Clock clock;

    public AuthService(
            UserRepo userRepo,
            RoleRepo roleRepo,
            SessionRepo sessionRepo,
            PasswordEncoder passwordEncoder,
            IJwtService jwtService,
            Clock clock
    ) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.sessionRepo = sessionRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public User signup(String name, String email, String phoneNumber, String password) {
        email = normalizeEmail(email);
        if (userRepo.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("User with email " + email + " already exists");
        }

        // Phone is optional; only enforce uniqueness when one was supplied.
        if (phoneNumber != null && !phoneNumber.isBlank() && userRepo.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new UserAlreadyExistsException("User with phone number " + phoneNumber + " already exists");
        }

        Role defaultRole = roleRepo.findByValue(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + DEFAULT_ROLE));

        String encodedPassword = passwordEncoder.encode(password);

        User user = User.builder()
                .name(name)
                .email(email)
                .phoneNumber(phoneNumber)
                .password(encodedPassword)
                .build();
        user.getRoles().add(defaultRole);

        try {
            return userRepo.save(user);
        } catch (DataIntegrityViolationException e) {
            // The pre-checks above race with concurrent signups; the unique constraints are the real guard.
            throw new UserAlreadyExistsException("User with this email or phone number already exists");
        }
    }

    @Override
    @Transactional
    public LoginResult login(String email, String password) {
        email = normalizeEmail(email);
        Optional<User> userOptional = userRepo.findByEmail(email);
        if (userOptional.isEmpty()) {
            throw new UserNotRegisteredException("User with email " + email + " is not registered");
        }
        User user = userOptional.get();
        if (user.getStatus() != Status.ACTIVE) {
            throw new UserNotRegisteredException("User with email " + email + " is not active");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new PasswordMismatchException("Password mismatch: Please type the correct password");
        }

        IssuedToken issued = jwtService.issue(user);

        UserSession userSession = UserSession.builder()
                .tokenHash(TokenHasher.sha256Hex(issued.token()))
                .expiresAt(LocalDateTime.ofInstant(issued.expiresAt(), clock.getZone()))
                .user(user)
                .build();
        sessionRepo.save(userSession);

        return new LoginResult(user, issued.token(), issued.expiresAt());
    }

    @Override
    @Transactional
    public TokenValidationResult validateToken(String token) {
        // Verify the signature before touching the DB so forged or garbage tokens never cost a query.
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (ExpiredJwtException e) {
            // Soft-delete the session so the row stays for auditing and the cleanup job has nothing to guess at.
            sessionRepo.findByTokenHash(TokenHasher.sha256Hex(token))
                    .filter(session -> session.getStatus() == Status.ACTIVE)
                    .ifPresent(session -> {
                        session.setStatus(Status.INACTIVE);
                        sessionRepo.save(session);
                    });
            log.debug("Rejected expired token");
            return TokenValidationResult.invalid();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token: {}", e.getMessage());
            return TokenValidationResult.invalid();
        }

        Optional<UserSession> sessionOptional = sessionRepo.findByTokenHash(TokenHasher.sha256Hex(token));
        if (sessionOptional.isEmpty()) {
            log.debug("Rejected token with no matching session");
            return TokenValidationResult.invalid();
        }
        UserSession session = sessionOptional.get();
        if (session.getStatus() != Status.ACTIVE) {
            log.debug("Rejected token for revoked session {}", session.getId());
            return TokenValidationResult.invalid();
        }
        if (session.getUser().getStatus() != Status.ACTIVE) {
            log.debug("Rejected token for inactive user {}", session.getUser().getId());
            return TokenValidationResult.invalid();
        }

        return new TokenValidationResult(
                true,
                Long.parseLong(claims.getSubject()),
                claims.get(JwtService.CLAIM_EMAIL, String.class),
                rolesFromClaims(claims),
                claims.getExpiration().toInstant(),
                session.getId()
        );
    }

    @Override
    @Transactional
    public void logout(Long sessionId) {
        sessionRepo.findById(sessionId)
                .filter(session -> session.getStatus() == Status.ACTIVE)
                .ifPresent(session -> {
                    session.setStatus(Status.INACTIVE);
                    sessionRepo.save(session);
                });
    }

    private static List<String> rolesFromClaims(Claims claims) {
        Object roles = claims.get(JwtService.CLAIM_ROLES);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

}
