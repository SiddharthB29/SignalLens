package com.signallens.ingestion.event;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private EventRequest validRequest(
            String eventId,
            String serviceName,
            String environment,
            OffsetDateTime occurredAt,
            EventRequest.Severity severity,
            String message,
            String exceptionType,
            String stackTrace,
            String traceId,
            Map<String, Object> metadata
    ) {
        return new EventRequest(
                eventId,
                serviceName,
                environment,
                occurredAt,
                severity,
                message,
                exceptionType,
                stackTrace,
                traceId,
                metadata
        );
    }

    @Test
    void validEventShouldPassValidation() {
        EventRequest request = new EventRequest(
                "evt-10001",
                "payment-service",
                "development",
                OffsetDateTime.parse("2026-10-25T06:30:00Z"),
                EventRequest.Severity.ERROR,
                "Database connection timed out",
                "SQLTransientConnectionException",
                "Example stack trace",
                "trace-abc123",
                Map.of("endpoint", "/api/payments")
        );

        var violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void blankServiceNameShouldFailValidation() {
        EventRequest request = new EventRequest(
                "evt-10001",
                "   ",
                "development",
                OffsetDateTime.parse("2026-10-25T06:30:00Z"),
                EventRequest.Severity.ERROR,
                "Database connection timed out",
                null,
                null,
                null,
                null
        );

        var violations = validator.validate(request);

        assertTrue(
                violations.stream()
                        .anyMatch(v ->
                                v.getPropertyPath().toString()
                                        .equals("serviceName"))
        );
    }

    @Test
    void messageExceedingMaximumLengthShouldFailValidation() {
        String oversizedMessage = "x".repeat(2001);

        EventRequest request = new EventRequest(
                "evt-10001",
                "payment-service",
                "development",
                OffsetDateTime.parse("2026-10-25T06:30:00Z"),
                EventRequest.Severity.ERROR,
                oversizedMessage,
                null,
                null,
                null,
                null
        );

        var violations = validator.validate(request);

        assertTrue(
                violations.stream()
                        .anyMatch(v ->
                                v.getPropertyPath().toString()
                                        .equals("message"))
        );
    }

    @Test
    void missingEventIdShouldFailValidation() {
        EventRequest request = validRequest(
                null,
                "payment-service",
                "development",
                OffsetDateTime.parse("2026-10-25T06:30:00Z"),
                EventRequest.Severity.ERROR,
                "Database connection timed out",
                null,
                null,
                null,
                null
        );

        var violations = validator.validate(request);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString()
                                .equals("eventId"))
        );
    }

    @Test
    void oversizedTraceIdShouldFailValidation() {
        EventRequest request = validRequest(
                "evt-10001",
                "payment-service",
                "development",
                OffsetDateTime.parse("2026-10-25T06:30:00Z"),
                EventRequest.Severity.ERROR,
                "Database connection timed out",
                null,
                null,
                "t".repeat(129),
                null
        );

        var violations = validator.validate(request);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString()
                                .equals("traceId"))
        );
    }

    @Test
    void metadataWithMoreThan50EntriesShouldFailValidation() {
        Map<String, Object> metadata = new HashMap<>();

        for (int i = 0; i < 51; i++) {
            metadata.put("key-" + i, "value-" + i);
        }

        EventRequest request = validRequest(
                "evt-10001",
                "payment-service",
                "development",
                OffsetDateTime.parse("2026-10-25T06:30:00Z"),
                EventRequest.Severity.ERROR,
                "Database connection timed out",
                null,
                null,
                null,
                metadata
        );

        var violations = validator.validate(request);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString()
                                .equals("metadata"))
        );
    }
}