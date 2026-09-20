package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.configs.PasswordResetProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Development stand-in for a real mail sender: writes the reset link to the application log.
 * <p>
 * <b>This logs a live credential.</b> Before any shared or production deployment, replace it with
 * an SMTP-backed {@link IEmailService} (e.g. {@code spring-boot-starter-mail} + {@code JavaMailSender}).
 * Because callers only depend on the interface, that is a one-class swap.
 */
@Slf4j
@Service
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
