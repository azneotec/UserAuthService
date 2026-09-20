package com.azneotech.userauthservice.controllers;

import com.azneotech.userauthservice.configs.AuthConfig;
import com.azneotech.userauthservice.exceptions.InvalidPasswordResetTokenException;
import com.azneotech.userauthservice.exceptions.PasswordMismatchException;
import com.azneotech.userauthservice.exceptions.UserAlreadyExistsException;
import com.azneotech.userauthservice.exceptions.UserNotRegisteredException;
import com.azneotech.userauthservice.models.Role;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.security.WithMockAuthenticatedUser;
import com.azneotech.userauthservice.services.IAuthService;
import com.azneotech.userauthservice.services.IPasswordResetService;
import com.azneotech.userauthservice.services.LoginResult;
import com.azneotech.userauthservice.services.TokenValidationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(AuthConfig.class)
class AuthControllerTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-09-21T10:00:00Z");

    @Autowired MockMvc mockMvc;
    @MockBean IAuthService authService;
    @MockBean IPasswordResetService passwordResetService;

    private static User alice() {
        User user = User.builder().id(7L).name("Alice").email("alice@example.com").phoneNumber("+15551234567").build();
        user.getRoles().add(Role.builder().value("ROLE_USER").build());
        return user;
    }

    @Nested
    class Signup {

        @Test
        void returns201WithUserBody() throws Exception {
            when(authService.signup("Alice", "alice@example.com", "+15551234567", "secret123")).thenReturn(alice());

            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Alice","email":"alice@example.com","phoneNumber":"+15551234567","password":"secret123"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.email").value("alice@example.com"))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                    .andExpect(jsonPath("$", not(hasKey("password"))));
        }

        @Test
        void returns400WithFieldErrorsForInvalidBody() throws Exception {
            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"","email":"not-an-email","phoneNumber":"abc","password":"short"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.name").exists())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());
        }

        @Test
        void phoneNumberIsOptional() throws Exception {
            when(authService.signup(eq("Alice"), eq("alice@example.com"), isNull(), eq("secret123"))).thenReturn(alice());

            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Alice","email":"alice@example.com","password":"secret123"}
                                    """))
                    .andExpect(status().isCreated());
        }

        @Test
        void returns409WhenUserAlreadyExists() throws Exception {
            when(authService.signup(any(), any(), any(), any()))
                    .thenThrow(new UserAlreadyExistsException("User with email alice@example.com already exists"));

            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Alice","email":"alice@example.com","password":"secret123"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("User with email alice@example.com already exists"));
        }

        @Test
        void returns400ForMalformedJson() throws Exception {
            mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed request body"));
        }
    }

    @Nested
    class Login {

        @Test
        void returns200WithTokenInBodyAndNoCookie() throws Exception {
            when(authService.login("alice@example.com", "secret123"))
                    .thenReturn(new LoginResult(alice(), "jwt-token", EXPIRES_AT));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"alice@example.com","password":"secret123"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                    .andExpect(jsonPath("$.token").value("jwt-token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresAt").value("2026-09-21T10:00:00Z"))
                    .andExpect(jsonPath("$.user.id").value(7))
                    .andExpect(jsonPath("$.user.email").value("alice@example.com"));
        }

        @Test
        void unknownEmailAndWrongPasswordProduceIdentical401() throws Exception {
            when(authService.login("nobody@example.com", "x")).thenThrow(new UserNotRegisteredException("not registered"));
            when(authService.login("alice@example.com", "wrong")).thenThrow(new PasswordMismatchException("mismatch"));

            for (String body : List.of(
                    "{\"email\":\"nobody@example.com\",\"password\":\"x\"}",
                    "{\"email\":\"alice@example.com\",\"password\":\"wrong\"}")) {
                mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.status").value(401))
                        .andExpect(jsonPath("$.message").value(ControllerAdvisor.INVALID_CREDENTIALS_MESSAGE));
            }
        }

        @Test
        void returns400ForBlankFields() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"","password":""}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());
        }
    }

    @Nested
    class ValidateToken {

        @Test
        void returnsIdentityForValidToken() throws Exception {
            when(authService.validateToken("good"))
                    .thenReturn(new TokenValidationResult(true, 7L, "alice@example.com", List.of("ROLE_USER"), EXPIRES_AT, 99L));

            mockMvc.perform(post("/auth/validateToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"token":"good"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.userId").value(7))
                    .andExpect(jsonPath("$.email").value("alice@example.com"))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                    .andExpect(jsonPath("$.expiresAt").value("2026-09-21T10:00:00Z"))
                    .andExpect(jsonPath("$", not(hasKey("sessionId"))));
        }

        @Test
        void returns200WithValidFalseForBadToken() throws Exception {
            when(authService.validateToken("bad")).thenReturn(TokenValidationResult.invalid());

            mockMvc.perform(post("/auth/validateToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"token":"bad"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$", not(hasKey("userId"))))
                    .andExpect(jsonPath("$", not(hasKey("email"))));
        }

        @Test
        void returns400ForBlankToken() throws Exception {
            mockMvc.perform(post("/auth/validateToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"token":"  "}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.token").exists());
        }
    }

    @Nested
    class Logout {

        @Test
        @WithMockAuthenticatedUser(sessionId = 123L)
        void returns204AndRevokesCallersSession() throws Exception {
            mockMvc.perform(post("/auth/logout"))
                    .andExpect(status().isNoContent());

            verify(authService).logout(123L);
        }

        @Test
        void returns401WithoutAuthentication() throws Exception {
            mockMvc.perform(post("/auth/logout"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Authentication required"));
        }
    }

    @Nested
    class ForgotPassword {

        @Test
        void returns200WithConstantMessage() throws Exception {
            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"alice@example.com"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(AuthController.FORGOT_PASSWORD_MESSAGE));

            verify(passwordResetService).requestReset("alice@example.com");
        }

        @Test
        void responseIsIdenticalForUnknownEmail() throws Exception {
            // The service is a no-op for unknown emails; the controller must not behave any differently.
            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"nobody@example.com"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(AuthController.FORGOT_PASSWORD_MESSAGE));
        }

        @Test
        void returns400ForMalformedEmail() throws Exception {
            mockMvc.perform(post("/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"not-an-email"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());

            verifyNoInteractions(passwordResetService);
        }
    }

    @Nested
    class ResetPassword {

        @Test
        void returns200OnSuccess() throws Exception {
            mockMvc.perform(post("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"token":"raw-token","newPassword":"new-secret-1"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(AuthController.RESET_PASSWORD_MESSAGE));

            verify(passwordResetService).resetPassword("raw-token", "new-secret-1");
        }

        @Test
        void returns400ForWeakPassword() throws Exception {
            mockMvc.perform(post("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"token":"raw-token","newPassword":"short"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

            verifyNoInteractions(passwordResetService);
        }

        @Test
        void returns400ForInvalidToken() throws Exception {
            doThrow(new InvalidPasswordResetTokenException("expired"))
                    .when(passwordResetService).resetPassword("stale", "new-secret-1");

            mockMvc.perform(post("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"token":"stale","newPassword":"new-secret-1"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Invalid or expired password reset token"));
        }
    }

}
