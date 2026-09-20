package com.azneotech.userauthservice.configs;

import com.azneotech.userauthservice.security.JwtAuthenticationEntryPoint;
import com.azneotech.userauthservice.security.JwtAuthenticationFilter;
import com.azneotech.userauthservice.services.IAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class AuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Stateless bearer-token security. Everything is authenticated except the endpoints a client
     * needs before it has a token (signup, login, password reset), the introspection endpoint
     * other services call, and the health probe.
     * <p>
     * {@code authService} is {@code @Lazy} because it depends on {@link #passwordEncoder()} from
     * this same class; the filter only touches it per request, so late resolution is harmless.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity,
                                                   @Lazy IAuthService authService,
                                                   ObjectMapper objectMapper) throws Exception {
        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new JwtAuthenticationEntryPoint(objectMapper)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/auth/signup",
                                "/auth/login",
                                "/auth/validateToken",
                                "/auth/forgot-password",
                                "/auth/reset-password").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(authService), UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

}
