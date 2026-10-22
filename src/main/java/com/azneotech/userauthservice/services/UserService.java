package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.exceptions.UserAlreadyExistsException;
import com.azneotech.userauthservice.exceptions.UserNotFoundException;
import com.azneotech.userauthservice.models.Role;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.repos.RoleRepo;
import com.azneotech.userauthservice.repos.SessionRepo;
import com.azneotech.userauthservice.repos.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
public class UserService implements IUserService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final SessionRepo sessionRepo;

    public UserService(UserRepo userRepo, RoleRepo roleRepo, SessionRepo sessionRepo) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.sessionRepo = sessionRepo;
    }

    @Override
    public User getById(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, String name, String phoneNumber) {
        User user = getById(userId);

        if (name != null) {
            user.setName(name);
        }

        if (phoneNumber != null && !Objects.equals(phoneNumber, user.getPhoneNumber())) {
            boolean takenByAnotherUser = userRepo.findByPhoneNumber(phoneNumber)
                    .map(other -> !other.getId().equals(userId))
                    .orElse(false);
            if (takenByAnotherUser) {
                throw new UserAlreadyExistsException("User with phone number " + phoneNumber + " already exists");
            }
            user.setPhoneNumber(phoneNumber);
        }

        try {
            return userRepo.save(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent signup/update using the same phone number.
            throw new UserAlreadyExistsException("User with phone number " + phoneNumber + " already exists");
        }
    }

    @Override
    @Transactional
    public User promoteToAdmin(Long userId) {
        User user = getById(userId);

        boolean alreadyAdmin = user.getRoles().stream().anyMatch(role -> ADMIN_ROLE.equals(role.getValue()));
        if (alreadyAdmin) {
            return user;
        }

        Role adminRole = roleRepo.findByValue(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("Role " + ADMIN_ROLE + " is not seeded"));
        user.getRoles().add(adminRole);
        User saved = userRepo.save(user);

        int revoked = sessionRepo.deactivateAllActiveForUser(userId);
        log.info("Granted {} to user {}; revoked {} session(s)", ADMIN_ROLE, userId, revoked);
        return saved;
    }

}
