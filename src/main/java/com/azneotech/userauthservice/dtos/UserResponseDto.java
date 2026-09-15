package com.azneotech.userauthservice.dtos;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {

    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    @Builder.Default
    private List<String> roles = new ArrayList<>();

}
