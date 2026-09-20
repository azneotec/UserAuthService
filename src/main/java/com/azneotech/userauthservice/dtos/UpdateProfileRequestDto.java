package com.azneotech.userauthservice.dtos;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Partial update: a null field means "leave unchanged". Email and password are deliberately not
 * editable here (email would need re-verification; password goes through the reset flow).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequestDto {

    @Size(min = 1, max = 100)
    private String name;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7-15 digits, optionally prefixed with +")
    private String phoneNumber;

}
