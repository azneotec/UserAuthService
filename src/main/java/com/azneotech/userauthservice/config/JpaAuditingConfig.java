package com.azneotech.userauthservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept out of the main application class so web-layer slice tests
 * (@WebMvcTest) don't pull it in and fail with "JPA metamodel must not
 * be empty" when they have no EntityManagerFactory configured.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
