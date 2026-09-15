package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.exceptions.PasswordMismatchException;
import com.azneotech.userauthservice.exceptions.UserAlreadyExistsException;
import com.azneotech.userauthservice.exceptions.UserNotRegisteredException;
import com.azneotech.userauthservice.models.Role;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.repos.RoleRepo;
import com.azneotech.userauthservice.repos.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService implements IAuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepo userRepo, RoleRepo roleRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
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
    public User login(String email, String password) {
        Optional<User> userOptional = userRepo.findByEmail(email);
        if (userOptional.isEmpty()) {
            throw new UserNotRegisteredException("User with email " + email + " is not registered");
        }
        User user = userOptional.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new PasswordMismatchException("Password mismatch: Please type the correct password");
        }

        return user;
    }
}
