package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.exceptions.UserAlreadyExistsException;
import com.azneotech.userauthservice.exceptions.UserNotFoundException;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.repos.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepo userRepo;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepo);
    }

    private static User alice() {
        return User.builder().id(7L).name("Alice").email("alice@example.com").phoneNumber("+15551234567").build();
    }

    @Test
    void getById_returnsUser() {
        when(userRepo.findById(7L)).thenReturn(Optional.of(alice()));

        assertThat(userService.getById(7L).getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void getById_throwsWhenMissing() {
        when(userRepo.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(404L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateProfile_changesOnlyName() {
        User user = alice();
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));
        when(userRepo.save(user)).thenReturn(user);

        User updated = userService.updateProfile(7L, "Alicia", null);

        assertThat(updated.getName()).isEqualTo("Alicia");
        assertThat(updated.getPhoneNumber()).isEqualTo("+15551234567");
        verify(userRepo, never()).findByPhoneNumber(anyString());
    }

    @Test
    void updateProfile_changesPhoneWhenFree() {
        User user = alice();
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));
        when(userRepo.findByPhoneNumber("+15559999999")).thenReturn(Optional.empty());
        when(userRepo.save(user)).thenReturn(user);

        User updated = userService.updateProfile(7L, null, "+15559999999");

        assertThat(updated.getName()).isEqualTo("Alice");
        assertThat(updated.getPhoneNumber()).isEqualTo("+15559999999");
    }

    @Test
    void updateProfile_skipsLookupWhenPhoneUnchanged() {
        User user = alice();
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));
        when(userRepo.save(user)).thenReturn(user);

        userService.updateProfile(7L, null, "+15551234567");

        verify(userRepo, never()).findByPhoneNumber(anyString());
    }

    @Test
    void updateProfile_rejectsPhoneOwnedByAnotherUser() {
        User bob = User.builder().id(8L).phoneNumber("+15559999999").build();
        when(userRepo.findById(7L)).thenReturn(Optional.of(alice()));
        when(userRepo.findByPhoneNumber("+15559999999")).thenReturn(Optional.of(bob));

        assertThatThrownBy(() -> userService.updateProfile(7L, null, "+15559999999"))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateProfile_translatesUniqueConstraintRace() {
        User user = alice();
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));
        when(userRepo.findByPhoneNumber("+15559999999")).thenReturn(Optional.empty());
        when(userRepo.save(user)).thenThrow(new DataIntegrityViolationException("uk_user_phone_number"));

        assertThatThrownBy(() -> userService.updateProfile(7L, null, "+15559999999"))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void updateProfile_throwsWhenUserMissing() {
        when(userRepo.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(404L, "X", null)).isInstanceOf(UserNotFoundException.class);
    }

}
