package com.azneotech.userauthservice.security;

import com.azneotech.userauthservice.configs.AuthConfig;
import com.azneotech.userauthservice.controllers.AuthController;
import com.azneotech.userauthservice.services.IAuthService;
import com.azneotech.userauthservice.services.IPasswordResetService;
import com.azneotech.userauthservice.services.TokenValidationResult;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real {@link AuthConfig} security chain and {@link JwtAuthenticationFilter} end to end
 * through MockMvc, with only the service mocked. {@code POST /auth/logout} is the protected endpoint
 * under test; {@code POST /auth/login} the public one.
 */
@WebMvcTest(AuthController.class)
@Import(AuthConfig.class)
class JwtAuthenticationFilterTest {

    @Autowired MockMvc mockMvc;
    @MockBean IAuthService authService;
    @MockBean IPasswordResetService passwordResetService; // AuthController dependency, unused here

    private static TokenValidationResult validResult() {
        return new TokenValidationResult(true, 7L, "alice@example.com", List.of("ROLE_USER"),
                Instant.parse("2026-09-21T10:00:00Z"), 7L);
    }

    @Test
    void validBearerTokenAuthenticatesTheRequest() throws Exception {
        when(authService.validateToken("good-token")).thenReturn(validResult());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isNoContent());

        verify(authService).logout(7L);
    }

    @Test
    void bearerPrefixIsCaseInsensitiveAndWhitespaceTolerant() throws Exception {
        when(authService.validateToken("good-token")).thenReturn(validResult());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "bearer   good-token  "))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidBearerTokenGets401Json() throws Exception {
        when(authService.validateToken("bad-token")).thenReturn(TokenValidationResult.invalid());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(authService, never()).logout(any());
    }

    @Test
    void missingAuthorizationHeaderGets401WithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).validateToken(anyString());
    }

    @Test
    void nonBearerSchemeIsIgnored() throws Exception {
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).validateToken(anyString());
    }

    @Test
    void publicEndpointsDoNotRequireAToken() throws Exception {
        // Each reaches the controller: 400 from validation, not 401 from security.
        for (String path : List.of("/auth/signup", "/auth/login", "/auth/validateToken",
                "/auth/forgot-password", "/auth/reset-password")) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void unknownPathsAreProtectedByDefault() throws Exception {
        mockMvc.perform(get("/something/else"))
                .andExpect(status().isUnauthorized());
    }

}
