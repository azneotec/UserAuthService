package com.azneotech.userauthservice.controllers;

import com.azneotech.userauthservice.dtos.AuthSignupRequestDto;
import com.azneotech.userauthservice.dtos.UserResponseDto;
import com.azneotech.userauthservice.dtos.LoginRequestDto;
import com.azneotech.userauthservice.models.Role;
import com.azneotech.userauthservice.models.User;
import com.azneotech.userauthservice.services.IAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponseDto> signup(@RequestBody AuthSignupRequestDto requestDto) {
        User user = authService.signup(
                requestDto.getName(),
                requestDto.getEmail(),
                requestDto.getPhoneNumber(),
                requestDto.getPassword()
        );
        UserResponseDto responseDto = mapUserToUserResponseDto(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponseDto> login(@RequestBody LoginRequestDto requestDto) {
        User user = authService.login(
                requestDto.getEmail(),
                requestDto.getPassword()
        );
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserResponseDto responseDto = mapUserToUserResponseDto(user);
        return ResponseEntity.status(HttpStatus.OK).body(responseDto);
    }

    private UserResponseDto mapUserToUserResponseDto(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getValue).toList();
        return UserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .roles(roles)
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<String> login() {
        return ResponseEntity.ok().build();
    }

}
