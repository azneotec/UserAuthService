package com.azneotech.userauthservice.controllers;

import com.azneotech.userauthservice.configs.AuthConfig;
import com.azneotech.userauthservice.exceptions.UserAlreadyExistsException;
import com.azneotech.userauthservice.exceptions.UserNotFoundException;
import com.azneotech.userauthservice.models.Role;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.security.WithMockAuthenticatedUser;
import com.azneotech.userauthservice.services.IAuthService;
import com.azneotech.userauthservice.services.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(AuthConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean IUserService userService;
    // Needed by AuthConfig's filter chain; unused here because @WithMockAuthenticatedUser bypasses the filter.
    @MockBean IAuthService authService;

    private static User alice() {
        User user = User.builder().id(7L).name("Alice").email("alice@example.com").phoneNumber("+15551234567").build();
        user.getRoles().add(Role.builder().value("ROLE_USER").build());
        return user;
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L)
    void getProfile_returnsCallersProfile() throws Exception {
        when(userService.getById(7L)).thenReturn(alice());

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("+15551234567"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$", not(hasKey("password"))));
    }

    @Test
    void getProfile_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L)
    void getProfile_returns404WhenUserRowIsGone() throws Exception {
        when(userService.getById(7L)).thenThrow(new UserNotFoundException("User with id 7 not found"));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User with id 7 not found"));
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L)
    void updateProfile_returnsUpdatedProfile() throws Exception {
        User updated = alice();
        updated.setName("Alicia");
        when(userService.updateProfile(7L, "Alicia", null)).thenReturn(updated);

        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alicia"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alicia"));
    }

    @Test
    @WithMockAuthenticatedUser
    void updateProfile_returns400ForInvalidPhone() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"not-a-phone"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockAuthenticatedUser
    void updateProfile_returns400ForEmptyName() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L)
    void updateProfile_returns409WhenPhoneTaken() throws Exception {
        when(userService.updateProfile(anyLong(), isNull(), any()))
                .thenThrow(new UserAlreadyExistsException("User with phone number +15559999999 already exists"));

        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+15559999999"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateProfile_requiresAuthentication() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

}
