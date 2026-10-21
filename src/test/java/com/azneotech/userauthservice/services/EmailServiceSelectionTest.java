package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.clients.KafkaProducerClient;
import com.azneotech.userauthservice.configs.PasswordResetProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** {@code password-reset.delivery} must leave exactly one {@link IEmailService} for the app to start. */
class EmailServiceSelectionTest {

    @Configuration
    @Import({KafkaEmailService.class, LoggingEmailService.class})
    static class Beans {
        @Bean KafkaProducerClient kafkaProducerClient() { return mock(KafkaProducerClient.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean PasswordResetProperties passwordResetProperties() {
            return new PasswordResetProperties(Duration.ofMinutes(15), "http://localhost:3000/reset-password?token=");
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Beans.class);

    @Test
    void usesKafkaWhenDeliveryIsNotConfigured() {
        runner.run(context -> assertThat(context).getBean(IEmailService.class).isInstanceOf(KafkaEmailService.class));
    }

    @Test
    void usesKafkaWhenDeliveryIsKafka() {
        runner.withPropertyValues("password-reset.delivery=kafka")
                .run(context -> assertThat(context).getBean(IEmailService.class).isInstanceOf(KafkaEmailService.class));
    }

    @Test
    void usesLoggingWhenDeliveryIsLog() {
        runner.withPropertyValues("password-reset.delivery=log")
                .run(context -> assertThat(context).getBean(IEmailService.class).isInstanceOf(LoggingEmailService.class));
    }
}
