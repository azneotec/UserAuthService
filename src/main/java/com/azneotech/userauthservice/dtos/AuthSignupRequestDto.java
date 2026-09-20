package com.azneotech.userauthservice.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthSignupRequestDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    // 72 is BCrypt's input limit; longer passwords are silently truncated by the encoder.
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7-15 digits, optionally prefixed with +")
    private String phoneNumber;

}
