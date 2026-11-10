package com.signallens.consumer.service;

import com.signallens.consumer.model.*;
import com.signallens.consumer.processor.FailureFingerprintGenerator;
import com.signallens.consumer.repository.FailureGroupRepository;
import com.signallens.consumer.repository.FailureOccurrenceRepository;
import com.signallens.consumer.repository.ProcessedEventRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
class FailureGroupingServiceIntegrationTest {

    @Autowired
    private FailureGroupingService failureGroupingService;

    @Autowired
    private FailureGroupRepository failureGroupRepository;

    @Autowired
    private FailureOccurrenceRepository failureOccurrenceRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private FailureFingerprintGenerator fingerprintGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String testEventId;
    private String testFingerprint;


    @Test
    @Transactional
    void firstFailureOccurrenceShouldCreateGroupAndOccurrence() {

        // Step 1: Create a test event.
        testEventId = "fg-test-" + UUID.randomUUID();

        OffsetDateTime occurredAt = OffsetDateTime.now();

        IngestedEvent event = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                occurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Test stack trace",
                "test-trace-001",
                null
        );

        testFingerprint = fingerprintGenerator.generate(event);

        // Step 2: Persist the processed event.
        int insertedRows = processedEventRepository.insertIfAbsent(
                event.eventId(),
                event.serviceName(),
                event.environment().name(),
                event.severity().name(),
                "TEST",
                event.message(),
                event.occurredAt(),
                OffsetDateTime.now(),
                event.traceId()
        );

        assertEquals(1, insertedRows);

        // Step 3: Group the event.
        failureGroupingService.group(event);

        // Step 4: Verify that the failure group exists.
        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals("payment-service", failureGroup.getServiceName());
        assertEquals(Environment.PRODUCTION, failureGroup.getEnvironment());
        assertEquals(
                FailureGroupStatus.OPEN,
                failureGroup.getStatus()
        );

        // Step 5: Verify the occurrence count.
        assertEquals(1L, failureGroup.getOccurrenceCount());

        // Step 6: Verify the occurrence was persisted.
        Integer occurrenceCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_occurrences
                WHERE event_id = ?
                """,
                Integer.class,
                testEventId
        );

        assertNotNull(occurrenceCount);
        assertEquals(1, occurrenceCount);
    }

    @Test
    @Transactional
    void groupShouldMaintainCorrectFirstSeenAndLastSeenTimestamps() {

        // Step 1: Create three unique event IDs.
        testEventId = "fg-timestamp-" + UUID.randomUUID();
        secondTestEventId = "fg-timestamp-" + UUID.randomUUID();

        thirdTestEventId = "fg-timestamp-" + UUID.randomUUID();

        // Step 2: Define timestamps in chronological order.
        OffsetDateTime baseTime = OffsetDateTime.now()
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        OffsetDateTime firstOccurredAt = baseTime;
        OffsetDateTime secondOccurredAt = baseTime.plusMinutes(10);
        OffsetDateTime thirdOccurredAt = baseTime.plusMinutes(5);

        // Step 3: Create three matching events.
        IngestedEvent firstEvent = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                firstOccurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "First stack trace",
                "timestamp-trace-001",
                null
        );

        IngestedEvent secondEvent = new IngestedEvent(
                secondTestEventId,
                "payment-service",
                Environment.PRODUCTION,
                secondOccurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Second stack trace",
                "timestamp-trace-002",
                null
        );

        IngestedEvent thirdEvent = new IngestedEvent(
                thirdTestEventId,
                "payment-service",
                Environment.PRODUCTION,
                thirdOccurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Third stack trace",
                "timestamp-trace-003",
                null
        );

        testFingerprint = fingerprintGenerator.generate(firstEvent);

        // Step 4: Persist all three processed events.
        for (IngestedEvent event :
                java.util.List.of(firstEvent, secondEvent, thirdEvent)) {

            int insertedRows = processedEventRepository.insertIfAbsent(
                    event.eventId(),
                    event.serviceName(),
                    event.environment().name(),
                    event.severity().name(),
                    "TEST",
                    event.message(),
                    event.occurredAt(),
                    OffsetDateTime.now(),
                    event.traceId()
            );

            assertEquals(1, insertedRows);
        }

        // Step 5: Process events in this order: 10:00, 10:10, 10:05.
        failureGroupingService.group(firstEvent);
        failureGroupingService.group(secondEvent);
        failureGroupingService.group(thirdEvent);

        // Step 6: Retrieve the failure group.
        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        // Step 7: Verify timestamps and occurrence count.
        assertEquals(
                baseTime.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                failureGroup.getFirstSeenAt().toInstant()
                        .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        );

        assertEquals(
                secondOccurredAt.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                failureGroup.getLastSeenAt().toInstant()
                        .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        );
        assertEquals(3L, failureGroup.getOccurrenceCount());
    }

    private String secondTestEventId;
    private String thirdTestEventId;
    @AfterEach
    void cleanUpTestData() {

        if (testEventId != null) {
            jdbcTemplate.update(
                    "DELETE FROM failure_occurrences WHERE event_id = ?",
                    testEventId
            );

            jdbcTemplate.update(
                    "DELETE FROM processed_events WHERE event_id = ?",
                    testEventId
            );
        }

        if (secondTestEventId != null) {
            jdbcTemplate.update(
                    "DELETE FROM failure_occurrences WHERE event_id = ?",
                    secondTestEventId
            );

            jdbcTemplate.update(
                    "DELETE FROM processed_events WHERE event_id = ?",
                    secondTestEventId
            );
        }

        if (thirdTestEventId != null) {
            jdbcTemplate.update(
                    "DELETE FROM failure_occurrences WHERE event_id = ?",
                    thirdTestEventId
            );

            jdbcTemplate.update(
                    "DELETE FROM processed_events WHERE event_id = ?",
                    thirdTestEventId
            );
        }

        if (testFingerprint != null) {
            failureGroupRepository.findByFingerprint(testFingerprint)
                    .ifPresent(group ->
                            failureGroupRepository.deleteById(group.getId())
                    );
        }
    }

    @Test
    @Transactional
    void duplicateFailureOccurrenceShouldNotIncreaseOccurrenceCount() {

        testEventId = "fg-test-" + UUID.randomUUID();

        OffsetDateTime occurredAt = OffsetDateTime.now();

        IngestedEvent event = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                occurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Test stack trace",
                "test-trace-002",
                null
        );

        testFingerprint = fingerprintGenerator.generate(event);

        int insertedRows = processedEventRepository.insertIfAbsent(
                event.eventId(),
                event.serviceName(),
                event.environment().name(),
                event.severity().name(),
                "TEST",
                event.message(),
                event.occurredAt(),
                OffsetDateTime.now(),
                event.traceId()
        );

        assertEquals(1, insertedRows);

        // First processing.
        failureGroupingService.group(event);

        // Duplicate processing of the same event.
        failureGroupingService.group(event);

        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals(1L, failureGroup.getOccurrenceCount());

        Integer occurrenceCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_occurrences
                WHERE event_id = ?
                """,
                Integer.class,
                testEventId
        );

        assertNotNull(occurrenceCount);
        assertEquals(1, occurrenceCount);
    }

    @Test
    @Transactional
    void differentEventsWithSameFailureCharacteristicsShouldShareGroup() {

        String firstEventId = "fg-test-" + UUID.randomUUID();
        secondTestEventId = "fg-test-" + UUID.randomUUID();

        OffsetDateTime firstOccurredAt = OffsetDateTime.now();
        OffsetDateTime secondOccurredAt = firstOccurredAt.plusSeconds(10);

        IngestedEvent firstEvent = new IngestedEvent(
                firstEventId,
                "payment-service",
                Environment.PRODUCTION,
                firstOccurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "First stack trace",
                "trace-001",
                null
        );

        IngestedEvent secondEvent = new IngestedEvent(
                secondTestEventId,
                "payment-service",
                Environment.PRODUCTION,
                secondOccurredAt,
                Severity.FATAL,
                "  PAYMENT PROCESSING FAILED  ",
                "NullPointerException",
                "Different stack trace",
                "trace-002",
                null
        );

        testFingerprint = fingerprintGenerator.generate(firstEvent);

        assertEquals(
                testFingerprint,
                fingerprintGenerator.generate(secondEvent)
        );

        // Persist both processed events.
        int firstInsertedRows = processedEventRepository.insertIfAbsent(
                firstEvent.eventId(),
                firstEvent.serviceName(),
                firstEvent.environment().name(),
                firstEvent.severity().name(),
                "TEST",
                firstEvent.message(),
                firstEvent.occurredAt(),
                OffsetDateTime.now(),
                firstEvent.traceId()
        );

        int secondInsertedRows = processedEventRepository.insertIfAbsent(
                secondEvent.eventId(),
                secondEvent.serviceName(),
                secondEvent.environment().name(),
                secondEvent.severity().name(),
                "TEST",
                secondEvent.message(),
                secondEvent.occurredAt(),
                OffsetDateTime.now(),
                secondEvent.traceId()
        );

        assertEquals(1, firstInsertedRows);
        assertEquals(1, secondInsertedRows);

        // Group both events.
        failureGroupingService.group(firstEvent);
        failureGroupingService.group(secondEvent);

        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals(2L, failureGroup.getOccurrenceCount());

        Integer groupCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_groups
                WHERE fingerprint = ?
                """,
                Integer.class,
                testFingerprint
        );

        Integer occurrenceCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_occurrences
                WHERE failure_group_id = ?
                """,
                Integer.class,
                failureGroup.getId()
        );

        assertEquals(1, groupCount);
        assertEquals(2, occurrenceCount);
    }

    @Test
    @Transactional
    void resolvedFailureGroupShouldReopenWhenNewOccurrenceArrives() {

        testEventId = "fg-test-" + UUID.randomUUID();
        secondTestEventId = "fg-test-" + UUID.randomUUID();

        OffsetDateTime firstOccurredAt = OffsetDateTime.now();
        OffsetDateTime secondOccurredAt = firstOccurredAt.plusSeconds(10);

        IngestedEvent firstEvent = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                firstOccurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "First stack trace",
                "trace-001",
                null
        );

        IngestedEvent secondEvent = new IngestedEvent(
                secondTestEventId,
                "payment-service",
                Environment.PRODUCTION,
                secondOccurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Second stack trace",
                "trace-002",
                null
        );

        testFingerprint = fingerprintGenerator.generate(firstEvent);

        // Persist the first processed event.
        int firstInsertedRows = processedEventRepository.insertIfAbsent(
                firstEvent.eventId(),
                firstEvent.serviceName(),
                firstEvent.environment().name(),
                firstEvent.severity().name(),
                "TEST",
                firstEvent.message(),
                firstEvent.occurredAt(),
                OffsetDateTime.now(),
                firstEvent.traceId()
        );

        assertEquals(1, firstInsertedRows);

        // Create the initial failure group.
        failureGroupingService.group(firstEvent);

        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals(FailureGroupStatus.OPEN, failureGroup.getStatus());

        // Simulate someone resolving the failure group.
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE failure_groups
                SET status = 'RESOLVED'
                WHERE fingerprint = ?
                """,
                testFingerprint
        );

        assertEquals(1, updatedRows);

        // Persist a second, separate event with the same failure characteristics.
        int secondInsertedRows = processedEventRepository.insertIfAbsent(
                secondEvent.eventId(),
                secondEvent.serviceName(),
                secondEvent.environment().name(),
                secondEvent.severity().name(),
                "TEST",
                secondEvent.message(),
                secondEvent.occurredAt(),
                OffsetDateTime.now(),
                secondEvent.traceId()
        );

        assertEquals(1, secondInsertedRows);

        // Process the new occurrence.
        failureGroupingService.group(secondEvent);

        // Verify that the existing group was reopened.
        FailureGroupEntity reopenedGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals(FailureGroupStatus.OPEN, reopenedGroup.getStatus());
        assertEquals(2L, reopenedGroup.getOccurrenceCount());

        Integer groupCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_groups
                WHERE fingerprint = ?
                """,
                Integer.class,
                testFingerprint
        );

        assertEquals(1, groupCount);
    }

    @Test
    @Transactional
    void acknowledgedFailureGroupShouldRemainAcknowledgedWhenNewOccurrenceArrives() {

        testEventId = "fg-test-" + UUID.randomUUID();
        secondTestEventId = "fg-test-" + UUID.randomUUID();

        OffsetDateTime firstOccurredAt = OffsetDateTime.now();
        OffsetDateTime secondOccurredAt = firstOccurredAt.plusSeconds(10);

        IngestedEvent firstEvent = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                firstOccurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "First stack trace",
                "trace-001",
                null
        );

        IngestedEvent secondEvent = new IngestedEvent(
                secondTestEventId,
                "payment-service",
                Environment.PRODUCTION,
                secondOccurredAt,
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Second stack trace",
                "trace-002",
                null
        );

        testFingerprint = fingerprintGenerator.generate(firstEvent);

        // Persist the first processed event.
        int firstInsertedRows = processedEventRepository.insertIfAbsent(
                firstEvent.eventId(),
                firstEvent.serviceName(),
                firstEvent.environment().name(),
                firstEvent.severity().name(),
                "TEST",
                firstEvent.message(),
                firstEvent.occurredAt(),
                OffsetDateTime.now(),
                firstEvent.traceId()
        );

        assertEquals(1, firstInsertedRows);

        // Create the initial failure group.
        failureGroupingService.group(firstEvent);

        // Mark the group as acknowledged.
        int updatedRows = jdbcTemplate.update(
                """
                UPDATE failure_groups
                SET status = 'ACKNOWLEDGED'
                WHERE fingerprint = ?
                """,
                testFingerprint
        );

        assertEquals(1, updatedRows);

        // Persist a second event with matching failure characteristics.
        int secondInsertedRows = processedEventRepository.insertIfAbsent(
                secondEvent.eventId(),
                secondEvent.serviceName(),
                secondEvent.environment().name(),
                secondEvent.severity().name(),
                "TEST",
                secondEvent.message(),
                secondEvent.occurredAt(),
                OffsetDateTime.now(),
                secondEvent.traceId()
        );

        assertEquals(1, secondInsertedRows);

        // Process the new occurrence.
        failureGroupingService.group(secondEvent);

        // Verify that the group remains acknowledged.
        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals(FailureGroupStatus.ACKNOWLEDGED, failureGroup.getStatus());
        assertEquals(2L, failureGroup.getOccurrenceCount());

        Integer groupCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_groups
                WHERE fingerprint = ?
                """,
                Integer.class,
                testFingerprint
        );

        assertEquals(1, groupCount);
    }
}