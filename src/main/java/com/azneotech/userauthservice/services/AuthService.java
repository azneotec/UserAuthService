package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.models.Role;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.repos.RoleRepo;
import com.azneotech.userauthservice.repos.UserRepo;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService implements IAuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;

    public AuthService(UserRepo userRepo, RoleRepo roleRepo) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
    }

    @Override
    public User signup(String name, String email, String phoneNumber, String password) {
        Role defaultRole = roleRepo.findByValue(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + DEFAULT_ROLE));

        User user = User.builder()
                .name(name)
                .email(email)
                .phoneNumber(phoneNumber)
                .password(password)
                .build();
        user.getRoles().add(defaultRole);

        return userRepo.save(user);
    }

    @Override
    public User login(String email, String password) {
        Optional<User> userOptional = userRepo.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getPassword().equals(password)) {
                return user;
            }
        }
        return null;
    }
}
