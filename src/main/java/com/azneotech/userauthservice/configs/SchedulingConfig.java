package com.azneotech.userauthservice.configs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kept in its own class (like {@link JpaAuditingConfig}) so slice tests don't start the scheduler.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
