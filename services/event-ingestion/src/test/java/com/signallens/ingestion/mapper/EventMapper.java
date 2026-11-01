package com.signallens.ingestion.mapper;

import com.signallens.ingestion.event.EventRequest;
import com.signallens.ingestion.model.IngestedEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventMapperTest {

    private final EventMapper mapper = new EventMapper();

    @Test
    void shouldMapEventRequestToIngestedEvent() {

        Map<String, Object> metadata = Map.of(
                "paymentId", "PAY-100",
                "retryCount", 2
        );

        EventRequest request = new EventRequest(
                "evt-1001",
                "payment-service",
                EventRequest.Environment.PRODUCTION,
                OffsetDateTime.parse("2026-11-01T14:30:00+05:30"),
                EventRequest.Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "PaymentService.processPayment()",
                "trace-123",
                metadata
        );

        IngestedEvent expected = new IngestedEvent(
                "evt-1001",
                "payment-service",
                EventRequest.Environment.PRODUCTION,
                OffsetDateTime.parse("2026-11-01T14:30:00+05:30"),
                EventRequest.Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "PaymentService.processPayment()",
                "trace-123",
                metadata
        );

        IngestedEvent actual = mapper.toIngestedEvent(request);

        assertEquals(expected, actual);
    }
}