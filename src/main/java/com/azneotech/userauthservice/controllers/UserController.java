package com.azneotech.userauthservice.controllers;

import com.azneotech.userauthservice.dtos.UpdateProfileRequestDto;
import com.azneotech.userauthservice.dtos.UserResponseDto;
import com.azneotech.userauthservice.mappers.UserMapper;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.security.AuthenticatedUser;
import com.azneotech.userauthservice.services.IUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile endpoints for the caller identified by their bearer token. All routes require authentication.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userService.getById(principal.userId());
        return ResponseEntity.ok(UserMapper.toUserResponseDto(user));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponseDto> updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @Valid @RequestBody UpdateProfileRequestDto requestDto) {
        User user = userService.updateProfile(principal.userId(), requestDto.getName(), requestDto.getPhoneNumber());
        return ResponseEntity.ok(UserMapper.toUserResponseDto(user));
    }

}
