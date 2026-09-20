package com.azneotech.userauthservice.configs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Application-wide beans that are not security-specific. Kept separate from the main application
 * class (like {@link JpaAuditingConfig}) so web-layer slice tests don't pick them up.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, PasswordResetProperties.class})
public class AppConfig {

    /**
     * Single source of "now" for token issue/expiry and session timestamps, so tests can inject
     * {@link Clock#fixed} instead of racing the wall clock.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

}
