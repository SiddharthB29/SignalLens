package com.signallens.ingestion.event;

import jakarta.validation.ConstraintViolation;
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

import org.junit.jupiter.api.Assertions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EventRequestTest {

    @Autowired
    private ObjectMapper objectMapper;
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
            EventRequest.Environment environment,
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

    private EventRequest validRequestWithMetadata(Map<String, Object> metadata) {
        return validRequest(
                "evt-10001",
                "payment-service",
                EventRequest.Environment.DEVELOPMENT,
                OffsetDateTime.parse("2026-10-25T06:30:00Z"),
                EventRequest.Severity.ERROR,
                "Database connection timed out",
                null,
                null,
                null,
                metadata
        );
    }

    @Test
    void validEventShouldPassValidation() {
        EventRequest request = new EventRequest(
                "evt-10001",
                "payment-service",
                EventRequest.Environment.DEVELOPMENT,
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
                EventRequest.Environment.DEVELOPMENT,
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
                EventRequest.Environment.DEVELOPMENT,
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
                EventRequest.Environment.DEVELOPMENT,
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
                EventRequest.Environment.DEVELOPMENT,
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
                EventRequest.Environment.DEVELOPMENT,
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

    @Test
    void metadataWithinMaximumDepthShouldPassValidation() {
        Map<String, Object> metadata = Map.of(
                "level2", Map.of(
                        "level3", Map.of(
                                "level4", Map.of(
                                        "level5", "value"
                                )
                        )
                )
        );

        EventRequest request = validRequestWithMetadata(metadata);

        Set<ConstraintViolation<EventRequest>> violations =
                validator.validate(request);

        assertTrue(
                violations.stream().noneMatch(
                        violation -> violation.getPropertyPath()
                                .toString().equals("metadata")
                )
        );
    }

    @Test
    void metadataExceedingMaximumDepthShouldFailValidation() {
        Map<String, Object> metadata = Map.of(
                "level2", Map.of(
                        "level3", Map.of(
                                "level4", Map.of(
                                        "level5", Map.of(
                                                "level6", Map.of(
                                                        "level7", "value"
                                                )
                                        )
                                )
                        )
                )
        );

        EventRequest request = validRequestWithMetadata(metadata);

        Set<ConstraintViolation<EventRequest>> violations =
                validator.validate(request);

        assertTrue(
                violations.stream().anyMatch(
                        violation -> violation.getPropertyPath()
                                .toString().equals("metadata")
                )
        );
    }

    @Test
    void whitespaceShouldBeNormalized() {
        EventRequest request = new EventRequest(
                "  evt-whitespace-001  ",
                "  payment-service  ",
                EventRequest.Environment.DEVELOPMENT,
                OffsetDateTime.parse("2026-10-25T06:30:00Z"),
                EventRequest.Severity.ERROR,
                "  Testing whitespace normalization  ",
                "   ",
                null,
                "   ",
                null
        );

        assertEquals("evt-whitespace-001", request.eventId());
        assertEquals("payment-service", request.serviceName());
        assertEquals(
                "Testing whitespace normalization",
                request.message()
        );
        assertNull(request.exceptionType());
        assertNull(request.traceId());

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void numericTimestampShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-timestamp-001",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": 5757575,
                "severity": "ERROR",
                "message": "Testing numeric timestamp rejection"
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void timestampWithoutOffsetShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-timestamp-002",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00",
                "severity": "ERROR",
                "message": "Testing timestamp without offset"
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void validTimestampWithOffsetShouldPassDeserialization() {
        String json = """
            {
                "eventId": "evt-timestamp-003",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ERROR",
                "message": "Testing valid timestamp with offset"
            }
            """;

        EventRequest request = objectMapper.readValue(json, EventRequest.class);

        assertEquals(
                OffsetDateTime.parse("2026-10-25T10:30:00+05:30"),
                request.occurredAt()
        );
    }
    @Test
    void metadataArrayShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-metadata-001",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ERROR",
                "message": "Testing metadata array rejection",
                "metadata": ["one", "two"]
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void metadataStringShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-metadata-002",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ERROR",
                "message": "Testing metadata string rejection",
                "metadata": "some text"
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void metadataNumberShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-metadata-003",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ERROR",
                "message": "Testing metadata number rejection",
                "metadata": 123
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void metadataBooleanShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-metadata-004",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ERROR",
                "message": "Testing metadata boolean rejection",
                "metadata": true
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void validMetadataObjectShouldPassDeserialization() {
        String json = """
            {
                "eventId": "evt-metadata-005",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ERROR",
                "message": "Testing valid metadata object",
                "metadata": {
                    "endpoint": "/api/payments",
                    "retryCount": 3,
                    "region": "ap-south-1"
                }
            }
            """;

        EventRequest request = objectMapper.readValue(json, EventRequest.class);

        assertNotNull(request.metadata());
        assertEquals("/api/payments", request.metadata().get("endpoint"));
        assertEquals(3, request.metadata().get("retryCount"));
        assertEquals("ap-south-1", request.metadata().get("region"));
    }
    @Test
    void numericEnvironmentShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-enum-001",
                "serviceName": "payment-service",
                "environment": 123,
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ERROR",
                "message": "Testing numeric environment rejection"
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void booleanEnvironmentShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-enum-002",
                "serviceName": "payment-service",
                "environment": true,
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ERROR",
                "message": "Testing boolean environment rejection"
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void invalidEnvironmentShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-enum-003",
                "serviceName": "payment-service",
                "environment": "TESTING",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ERROR",
                "message": "Testing invalid environment rejection"
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void numericSeverityShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-enum-004",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": 123,
                "message": "Testing numeric severity rejection"
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void booleanSeverityShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-enum-005",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": true,
                "message": "Testing boolean severity rejection"
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void invalidSeverityShouldFailDeserialization() {
        String json = """
            {
                "eventId": "evt-enum-006",
                "serviceName": "payment-service",
                "environment": "development",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "DEBUG",
                "message": "Testing invalid severity rejection"
            }
            """;

        assertThrows(
                RuntimeException.class,
                () -> objectMapper.readValue(json, EventRequest.class)
        );
    }
    @Test
    void mixedCaseEnumValuesShouldPassDeserialization() {
        String json = """
            {
                "eventId": "evt-enum-007",
                "serviceName": "payment-service",
                "environment": "PrOdUcTiOn",
                "occurredAt": "2026-10-25T10:30:00+05:30",
                "severity": "ErRoR",
                "message": "Testing mixed-case enum acceptance"
            }
            """;

        EventRequest request = objectMapper.readValue(json, EventRequest.class);

        assertEquals(EventRequest.Environment.PRODUCTION, request.environment());
        assertEquals(EventRequest.Severity.ERROR, request.severity());
    }
}