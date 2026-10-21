package com.azneotech.userauthservice.clients;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class KafkaProducerClient {

    public static final String SIGNUP_TOPIC = "signup";
    public static final String PASSWORD_RESET_TOPIC = "password-reset";

    /** Sender address on every email message; NotificationService uses it as the SMTP login. */
    public static final String EMAIL_SENDER = "azneotech@gmail.com";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerClient(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String topic, String message) {
        kafkaTemplate.send(topic, message);
    }

    /**
     * Best-effort publish for messages that must not affect the caller's outcome. When a transaction is
     * active the message is sent only once it has committed (nothing is sent if it rolls back); otherwise
     * it is sent immediately. A Kafka failure is logged, never thrown.
     */
    public void sendAfterCommit(String topic, String message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendQuietly(topic, message);
                }
            });
        } else {
            sendQuietly(topic, message);
        }
    }

    private void sendQuietly(String topic, String message) {
        try {
            kafkaTemplate.send(topic, message).whenComplete((result, failure) -> {
                if (failure != null) {
                    log.warn("Could not publish message to Kafka topic '{}'", topic, failure);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Could not publish message to Kafka topic '{}'", topic, e);
        }
    }

}
