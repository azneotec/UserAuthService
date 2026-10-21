package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.clients.KafkaProducerClient;
import com.azneotech.userauthservice.configs.PasswordResetProperties;
import com.azneotech.userauthservice.dtos.EmailDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default {@link IEmailService}: hands the reset email to NotificationService over Kafka, the same way
 * the welcome email is sent at signup. The message carries the raw reset token, so treat the
 * {@code password-reset} topic as sensitive. Replace with {@link LoggingEmailService} for local runs
 * without Kafka via {@code password-reset.delivery=log}.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "password-reset.delivery", havingValue = "kafka", matchIfMissing = true)
public class KafkaEmailService implements IEmailService {

    private final KafkaProducerClient kafkaProducerClient;
    private final ObjectMapper objectMapper;
    private final PasswordResetProperties properties;

    public KafkaEmailService(KafkaProducerClient kafkaProducerClient,
                             ObjectMapper objectMapper,
                             PasswordResetProperties properties) {
        this.kafkaProducerClient = kafkaProducerClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        EmailDto emailDto = new EmailDto();
        emailDto.setTo(toEmail);
        emailDto.setFrom(KafkaProducerClient.EMAIL_SENDER);
        emailDto.setSubject("Reset your password");
        emailDto.setBody("Use this link to reset your password. It expires in "
                + properties.expiration().toMinutes() + " minutes and works once:\n"
                + properties.linkBaseUrl() + rawToken);

        try {
            kafkaProducerClient.sendAfterCommit(KafkaProducerClient.PASSWORD_RESET_TOPIC, objectMapper.writeValueAsString(emailDto));
        } catch (JsonProcessingException e) {
            // forgot-password answers the same way whether or not an email goes out, so only log.
            log.error("Could not serialize password reset email", e);
        }
    }

}
