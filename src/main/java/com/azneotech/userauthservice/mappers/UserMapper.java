package com.azneotech.userauthservice.mappers;

import com.azneotech.userauthservice.dtos.UserResponseDto;
import com.azneotech.userauthservice.models.Role;
import com.azneotech.userauthservice.models.User;

import java.util.List;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponseDto toUserResponseDto(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getValue).toList();
        return UserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .roles(roles)
                .build();
    }

}
