package com.azneotech.userauthservice.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponseDto {

    private String token;
    /** Always {@code "Bearer"}: send the token as {@code Authorization: Bearer <token>}. */
    @Builder.Default
    private String tokenType = "Bearer";
    private Instant expiresAt;
    private UserResponseDto user;

}
