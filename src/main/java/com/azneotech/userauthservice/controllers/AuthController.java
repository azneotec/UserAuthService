package com.azneotech.userauthservice.controllers;

import com.azneotech.userauthservice.dtos.AuthSignupRequestDto;
import com.azneotech.userauthservice.dtos.ForgotPasswordRequestDto;
import com.azneotech.userauthservice.dtos.LoginRequestDto;
import com.azneotech.userauthservice.dtos.LoginResponseDto;
import com.azneotech.userauthservice.dtos.MessageResponseDto;
import com.azneotech.userauthservice.dtos.ResetPasswordRequestDto;
import com.azneotech.userauthservice.dtos.UserResponseDto;
import com.azneotech.userauthservice.dtos.ValidateTokenRequestDto;
import com.azneotech.userauthservice.dtos.ValidateTokenResponseDto;
import com.azneotech.userauthservice.mappers.UserMapper;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.security.AuthenticatedUser;
import com.azneotech.userauthservice.services.IAuthService;
import com.azneotech.userauthservice.services.IPasswordResetService;
import com.azneotech.userauthservice.services.LoginResult;
import com.azneotech.userauthservice.services.TokenValidationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    static final String FORGOT_PASSWORD_MESSAGE =
            "If an account exists for that email, a password reset link has been sent.";
    static final String RESET_PASSWORD_MESSAGE = "Password has been reset. Please log in again.";

    private final IAuthService authService;
    private final IPasswordResetService passwordResetService;

    public AuthController(IAuthService authService, IPasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponseDto> signup(@Valid @RequestBody AuthSignupRequestDto requestDto) {
        User user = authService.signup(
                requestDto.getName(),
                requestDto.getEmail(),
                requestDto.getPhoneNumber(),
                requestDto.getPassword()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(UserMapper.toUserResponseDto(user));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto) {
        LoginResult result = authService.login(requestDto.getEmail(), requestDto.getPassword());

        LoginResponseDto responseDto = LoginResponseDto.builder()
                .token(result.token())
                .expiresAt(result.expiresAt())
                .user(UserMapper.toUserResponseDto(result.user()))
                .build();
        return ResponseEntity.ok(responseDto);
    }

    /**
     * Token introspection for other services. Always 200: the request itself succeeded and
     * {@code valid} carries the answer.
     */
    @PostMapping("/validateToken")
    public ResponseEntity<ValidateTokenResponseDto> validateToken(@Valid @RequestBody ValidateTokenRequestDto requestDto) {
        TokenValidationResult result = authService.validateToken(requestDto.getToken());

        ValidateTokenResponseDto responseDto = ValidateTokenResponseDto.builder()
                .valid(result.valid())
                .userId(result.userId())
                .email(result.email())
                .roles(result.roles())
                .expiresAt(result.expiresAt())
                .build();
        return ResponseEntity.ok(responseDto);
    }

    /** Revokes the session behind the bearer token that authenticated this request. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logout(principal.sessionId());
        return ResponseEntity.noContent().build();
    }

    /** Always 200 with the same body, whether or not the email is registered. */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponseDto> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto requestDto) {
        passwordResetService.requestReset(requestDto.getEmail());
        return ResponseEntity.ok(new MessageResponseDto(FORGOT_PASSWORD_MESSAGE));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponseDto> resetPassword(@Valid @RequestBody ResetPasswordRequestDto requestDto) {
        passwordResetService.resetPassword(requestDto.getToken(), requestDto.getNewPassword());
        return ResponseEntity.ok(new MessageResponseDto(RESET_PASSWORD_MESSAGE));
    }

}
