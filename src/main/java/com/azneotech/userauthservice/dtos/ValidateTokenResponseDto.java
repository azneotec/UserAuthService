package com.azneotech.userauthservice.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Introspection response for other microservices. An invalid token serializes as
 * {@code {"valid":false,"roles":[]}}; a valid one carries the identity fields callers need for authorization.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidateTokenResponseDto {

    private boolean valid;
    private Long userId;
    private String email;
    @Builder.Default
    private List<String> roles = new ArrayList<>();
    private Instant expiresAt;

}
