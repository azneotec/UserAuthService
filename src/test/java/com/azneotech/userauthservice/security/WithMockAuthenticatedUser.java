package com.azneotech.userauthservice.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Puts an {@link AuthenticatedUser} principal in the test security context. Spring's
 * {@code @WithMockUser} installs a {@code UserDetails} principal, which would make
 * {@code @AuthenticationPrincipal AuthenticatedUser} resolve to null in controllers.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockAuthenticatedUserSecurityContextFactory.class)
public @interface WithMockAuthenticatedUser {

    long userId() default 7L;

    String email() default "alice@example.com";

    long sessionId() default 99L;

    String[] roles() default {"ROLE_USER"};

}
