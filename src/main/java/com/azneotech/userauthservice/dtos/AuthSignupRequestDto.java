package com.azneotech.userauthservice.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthSignupRequestDto {

    private String name;
    private String email;
    private String password;
    private String phoneNumber;

}
