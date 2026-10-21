package com.azneotech.userauthservice.clients;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaProducerClientTest {

    @Mock KafkaTemplate<String, String> kafkaTemplate;

    KafkaProducerClient client;

    @BeforeEach
    void setUp() {
        client = new KafkaProducerClient(kafkaTemplate);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsImmediatelyWhenThereIsNoTransaction() {
        when(kafkaTemplate.send("signup", "m")).thenReturn(CompletableFuture.completedFuture(null));

        client.sendAfterCommit("signup", "m");

        verify(kafkaTemplate).send("signup", "m");
    }

    @Test
    void waitsForCommitWhenATransactionIsActive() {
        when(kafkaTemplate.send("signup", "m")).thenReturn(CompletableFuture.completedFuture(null));
        TransactionSynchronizationManager.initSynchronization();

        client.sendAfterCommit("signup", "m");
        verify(kafkaTemplate, never()).send("signup", "m");

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(kafkaTemplate).send("signup", "m");
    }

    @Test
    void sendsNothingWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        client.sendAfterCommit("signup", "m");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(kafkaTemplate, never()).send("signup", "m");
    }

    @Test
    void swallowsASynchronousKafkaFailure() {
        when(kafkaTemplate.send("signup", "m")).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> client.sendAfterCommit("signup", "m")).doesNotThrowAnyException();
    }

    @Test
    void swallowsAnAsynchronousKafkaFailure() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("metadata timeout"));
        when(kafkaTemplate.send("signup", "m")).thenReturn(failed);

        assertThatCode(() -> client.sendAfterCommit("signup", "m")).doesNotThrowAnyException();
    }
}
