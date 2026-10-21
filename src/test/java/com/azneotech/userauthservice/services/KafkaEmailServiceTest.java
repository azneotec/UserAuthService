package com.azneotech.userauthservice.services;

import com.azneotech.userauthservice.clients.KafkaProducerClient;
import com.azneotech.userauthservice.configs.PasswordResetProperties;
import com.azneotech.userauthservice.dtos.EmailDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaEmailServiceTest {

    private static final PasswordResetProperties PROPERTIES =
            new PasswordResetProperties(Duration.ofMinutes(15), "http://localhost:3000/reset-password?token=");

    @Mock KafkaProducerClient kafkaProducerClient;

    @Test
    void publishesResetLinkToThePasswordResetTopic() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        KafkaEmailService service = new KafkaEmailService(kafkaProducerClient, objectMapper, PROPERTIES);

        service.sendPasswordResetEmail("alice@example.com", "raw-token");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaProducerClient).sendAfterCommit(eq(KafkaProducerClient.PASSWORD_RESET_TOPIC), message.capture());
        EmailDto email = objectMapper.readValue(message.getValue(), EmailDto.class);
        assertThat(email.getTo()).isEqualTo("alice@example.com");
        assertThat(email.getFrom()).isEqualTo(KafkaProducerClient.EMAIL_SENDER);
        assertThat(email.getSubject()).isEqualTo("Reset your password");
        assertThat(email.getBody())
                .contains("http://localhost:3000/reset-password?token=raw-token")
                .contains("15 minutes");
    }
}
