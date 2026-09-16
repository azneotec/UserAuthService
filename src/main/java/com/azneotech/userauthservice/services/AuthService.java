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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;
import org.antlr.v4.runtime.misc.Pair;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.*;

@Service
public class AuthService implements IAuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final SessionRepo sessionRepo;
    private final PasswordEncoder passwordEncoder;
    private final SecretKey secretKey;

    public AuthService(
            UserRepo userRepo,
            RoleRepo roleRepo,
            SessionRepo sessionRepo,
            PasswordEncoder passwordEncoder,
            SecretKey secretKey
    ) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.sessionRepo = sessionRepo;
        this.passwordEncoder = passwordEncoder;
        this.secretKey = secretKey;
    }

    @Override
    public User signup(String name, String email, String phoneNumber, String password) {
        Optional<User> userOptional = userRepo.findByEmail(email);
        if (userOptional.isPresent()) {
            throw new UserAlreadyExistsException("User with email " + email + " already exists");
        }

        userOptional = userRepo.findByPhoneNumber(phoneNumber);
        if (userOptional.isPresent()) {
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

        return userRepo.save(user);
    }

    @Override
    public Pair<User, String> login(String email, String password) {
        Optional<User> userOptional = userRepo.findByEmail(email);
        if (userOptional.isEmpty()) {
            throw new UserNotRegisteredException("User with email " + email + " is not registered");
        }
        User user = userOptional.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new PasswordMismatchException("Password mismatch: Please type the correct password");
        }

        // Generate JWT, use Map DS for payload
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", user.getId());
        claims.put("issuer", "scaler");

        long currentTime = System.currentTimeMillis();
        long expirationTime = currentTime + Duration.ofMinutes(1).toMillis();
        claims.put("iat", currentTime);
        claims.put("exp", expirationTime);

        List<String> roles = new ArrayList<>();
        for (Role role : user.getRoles()) {
            roles.add(role.getValue());
        }
        claims.put("access", roles);

        String token = Jwts.builder()
                .claims(claims)
                .signWith(secretKey)
                .compact();

        UserSession userSession = UserSession.builder()
                .token(token)
                .user(user)
                .build();
        sessionRepo.save(userSession);

        return new Pair<>(user, token);
    }

    public Boolean validateToken(String token) {
        Optional<UserSession> userSessionOptional = sessionRepo.findByToken(token);
        if (userSessionOptional.isEmpty()) {
            return false;
        }

        JwtParser jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();

        Long expiry = claims.get("exp", Long.class);
        Long currentTime = System.currentTimeMillis();

        System.out.println("expiry: " + expiry);
        System.out.println("currentTime: " + currentTime);

        if (currentTime > expiry) {
            UserSession userSession = userSessionOptional.get();
//            userSession.setStatus(Status.INACTIVE);
//            sessionRepo.save(userSession);
            sessionRepo.deleteById(userSession.getId());

            System.out.println("Token has expired");
            return false;
        }

        return true;
    }
}
