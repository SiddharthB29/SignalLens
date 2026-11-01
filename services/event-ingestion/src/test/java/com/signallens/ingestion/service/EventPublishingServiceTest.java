package com.signallens.ingestion.service;

import com.signallens.ingestion.event.EventRequest;
import com.signallens.ingestion.model.IngestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventPublishingServiceTest {

    private final KafkaTemplate<String, IngestedEvent> kafkaTemplate =
            mock(KafkaTemplate.class);

    private final EventPublishingService publishingService =
            new EventPublishingService(kafkaTemplate, "events");

    @Test
    void shouldPublishEventSuccessfully() {

        IngestedEvent event = createEvent();

        CompletableFuture<SendResult<String, IngestedEvent>> kafkaFuture =
                CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send("events", event.eventId(), event))
                .thenReturn(kafkaFuture);

        CompletableFuture<Void> result = publishingService.publish(event);

        assertDoesNotThrow(result::join);

        verify(kafkaTemplate).send("events", event.eventId(), event);
    }

    @Test
    void shouldPropagateKafkaPublishingFailure() {

        IngestedEvent event = createEvent();

        CompletableFuture<SendResult<String, IngestedEvent>> kafkaFuture =
                new CompletableFuture<>();

        kafkaFuture.completeExceptionally(
                new RuntimeException("Kafka publishing failed")
        );

        when(kafkaTemplate.send("events", event.eventId(), event))
                .thenReturn(kafkaFuture);

        CompletableFuture<Void> result = publishingService.publish(event);

        assertTrue(result.isCompletedExceptionally());

        verify(kafkaTemplate).send("events", event.eventId(), event);
    }

    private IngestedEvent createEvent() {

        return new IngestedEvent(
                "evt-1001",
                "payment-service",
                EventRequest.Environment.PRODUCTION,
                OffsetDateTime.parse("2026-11-01T14:30:00+05:30"),
                EventRequest.Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "PaymentService.processPayment()",
                "trace-123",
                Map.of("paymentId", "PAY-100")
        );
    }
}