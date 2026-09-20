package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.exceptions.UserAlreadyExistsException;
import com.azneotech.userauthservice.exceptions.UserNotFoundException;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.repos.UserRepo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class UserService implements IUserService {

    private final UserRepo userRepo;

    public UserService(UserRepo userRepo) {
        this.userRepo = userRepo;
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

}
