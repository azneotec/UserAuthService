package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.configs.PasswordResetProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development fallback for when Kafka isn't running: writes the reset link to the application log.
 * Active only with {@code password-reset.delivery=log}; the default is {@link KafkaEmailService}.
 * <p>
 * <b>This logs a live credential.</b> Never enable it in a shared or production deployment.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "password-reset.delivery", havingValue = "log")
public class LoggingEmailService implements IEmailService {

    private final PasswordResetProperties properties;

    public LoggingEmailService(PasswordResetProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        log.info("[EMAIL STUB] Password reset link for {}: {}{}", toEmail, properties.linkBaseUrl(), rawToken);
    }

}
