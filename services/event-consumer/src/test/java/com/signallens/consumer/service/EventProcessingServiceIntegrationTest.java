package com.signallens.consumer.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.signallens.consumer.model.Environment;
import com.signallens.consumer.model.FailureGroupEntity;
import com.signallens.consumer.model.IngestedEvent;
import com.signallens.consumer.model.Severity;
import com.signallens.consumer.processor.FailureFingerprintGenerator;
import com.signallens.consumer.repository.FailureGroupRepository;
import com.signallens.consumer.repository.ProcessedEventRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
class EventProcessingServiceIntegrationTest {

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoSpyBean
    private FailureGroupRepository failureGroupRepository;

    @Autowired
    private FailureFingerprintGenerator fingerprintGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private FailureGroupingService failureGroupingService;

    private String testEventId;
    private String secondTestEventId;
    private String testFingerprint;

    @Test
    void processingNewEventShouldAutomaticallyCreateFailureGroup() {

        // Step 1: Create an event.
        testEventId = "eps-test-" + UUID.randomUUID();

        IngestedEvent event = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                OffsetDateTime.now(),
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Test stack trace",
                "test-trace-001",
                null
        );

        testFingerprint = fingerprintGenerator.generate(event);

        // Step 2: Process the event through EventProcessingService.
        eventProcessingService.process(event);

        // Step 3: Verify the processed event was persisted.
        assertTrue(processedEventRepository.existsById(testEventId));

        // Step 4: Verify a failure group was automatically created.
        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals("payment-service", failureGroup.getServiceName());
        assertEquals(1L, failureGroup.getOccurrenceCount());

        // Step 5: Process the exact same event again.
        eventProcessingService.process(event);

        // Step 6: Verify duplicate processing did not increase the count.
        FailureGroupEntity groupAfterDuplicate =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals(1L, groupAfterDuplicate.getOccurrenceCount());

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
    void groupingFailureShouldRollbackProcessedEventInsertion() {

        // Step 1: Create a unique event.
        testEventId = "eps-rollback-" + UUID.randomUUID();

        IngestedEvent event = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                OffsetDateTime.now(),
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Rollback test stack trace",
                "rollback-trace-001",
                null
        );

        testFingerprint = fingerprintGenerator.generate(event);

        // Step 2: Force failure during failure grouping.
        doThrow(new IllegalStateException("Simulated grouping failure"))
                .when(failureGroupingService)
                .group(event);

        // Step 3: Verify that processing throws the expected exception.
        assertThrows(
                IllegalStateException.class,
                () -> eventProcessingService.process(event)
        );

        // Step 4: Verify that the processed-event insertion was rolled back.
        assertFalse(processedEventRepository.existsById(testEventId));

        // Step 5: Verify that no failure occurrence was persisted.
        Integer occurrenceCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_occurrences
                WHERE event_id = ?
                """,
                Integer.class,
                testEventId
        );

        assertEquals(0, occurrenceCount);

        // Step 6: Verify that no failure group was persisted.
        Integer groupCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_groups
                WHERE fingerprint = ?
                """,
                Integer.class,
                testFingerprint
        );

        assertEquals(0, groupCount);
    }

    @Test
    void failureDuringGroupUpdateShouldRollbackAllDatabaseChanges() {

        // Step 1: Create a unique event.
        testEventId = "eps-rollback-" + UUID.randomUUID();

        IngestedEvent event = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                OffsetDateTime.now(),
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Rollback test stack trace",
                "rollback-trace-002",
                null
        );

        testFingerprint = fingerprintGenerator.generate(event);

        // Step 2: Force the group update to report failure.
        org.mockito.Mockito.doReturn(0)
                .when(failureGroupRepository)
                .updateAfterOccurrence(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(OffsetDateTime.class)
                );

        // Step 3: Verify that processing throws an exception.
        assertThrows(
                IllegalStateException.class,
                () -> eventProcessingService.process(event)
        );

        // Step 4: Verify that the processed event was rolled back.
        assertFalse(processedEventRepository.existsById(testEventId));

        // Step 5: Verify that no failure group remains.
        Integer groupCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_groups
                WHERE fingerprint = ?
                """,
                Integer.class,
                testFingerprint
        );

        assertEquals(0, groupCount);

        // Step 6: Verify that no failure occurrence remains.
        Integer occurrenceCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_occurrences
                WHERE event_id = ?
                """,
                Integer.class,
                testEventId
        );

        assertEquals(0, occurrenceCount);
    }

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

        if (testFingerprint != null) {
            failureGroupRepository.findByFingerprint(testFingerprint)
                    .ifPresent(group ->
                            failureGroupRepository.deleteById(group.getId())
                    );
        }
    }

    @Test
    void concurrentProcessingOfSameEventShouldCreateOnlyOneOccurrence() throws Exception {

        testEventId = "eps-concurrent-" + UUID.randomUUID();

        IngestedEvent event = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                OffsetDateTime.now(),
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Test stack trace",
                "concurrent-trace-001",
                null
        );

        testFingerprint = fingerprintGenerator.generate(event);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Both tasks wait until they are ready to start together.
            java.util.concurrent.CountDownLatch ready =
                    new java.util.concurrent.CountDownLatch(2);

            java.util.concurrent.CountDownLatch start =
                    new java.util.concurrent.CountDownLatch(1);

            Future<?> firstTask = executor.submit(() -> {
                ready.countDown();
                start.await();
                eventProcessingService.process(event);
                return null;
            });

            Future<?> secondTask = executor.submit(() -> {
                ready.countDown();
                start.await();
                eventProcessingService.process(event);
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));

            // Release both tasks.
            start.countDown();

            firstTask.get(30, TimeUnit.SECONDS);
            secondTask.get(30, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        // Verify only one processed event exists.
        Integer processedEventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_events
                WHERE event_id = ?
                """,
                Integer.class,
                testEventId
        );

        assertEquals(1, processedEventCount);

        // Verify only one failure group exists.
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

        // Verify only one occurrence exists.
        Integer occurrenceCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_occurrences
                WHERE event_id = ?
                """,
                Integer.class,
                testEventId
        );

        assertEquals(1, occurrenceCount);

        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals(1L, failureGroup.getOccurrenceCount());
    }

    @Test
    void concurrentMatchingEventsShouldShareOneFailureGroup() throws Exception {

        testEventId = "eps-concurrent-" + UUID.randomUUID();
        secondTestEventId = "eps-concurrent-" + UUID.randomUUID();

        OffsetDateTime occurredAt = OffsetDateTime.now();

        IngestedEvent firstEvent = new IngestedEvent(
                testEventId,
                "payment-service",
                Environment.PRODUCTION,
                occurredAt,
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
                occurredAt.plusSeconds(5),
                Severity.ERROR,
                " payment processing failed ",
                "NullPointerException",
                "Second stack trace",
                "trace-002",
                null
        );

        testFingerprint = fingerprintGenerator.generate(firstEvent);

        assertEquals(
                testFingerprint,
                fingerprintGenerator.generate(secondEvent)
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.CountDownLatch ready =
                    new java.util.concurrent.CountDownLatch(2);

            java.util.concurrent.CountDownLatch start =
                    new java.util.concurrent.CountDownLatch(1);

            Future<?> firstTask = executor.submit(() -> {
                ready.countDown();
                start.await();
                eventProcessingService.process(firstEvent);
                return null;
            });

            Future<?> secondTask = executor.submit(() -> {
                ready.countDown();
                start.await();
                eventProcessingService.process(secondEvent);
                return null;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));

            start.countDown();

            firstTask.get(30, TimeUnit.SECONDS);
            secondTask.get(30, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        Integer processedEventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_events
                WHERE event_id IN (?, ?)
                """,
                Integer.class,
                testEventId,
                secondTestEventId
        );

        assertEquals(2, processedEventCount);

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

        Integer occurrenceCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM failure_occurrences
                WHERE failure_group_id = (
                    SELECT id
                    FROM failure_groups
                    WHERE fingerprint = ?
                )
                """,
                Integer.class,
                testFingerprint
        );

        assertEquals(2, occurrenceCount);

        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(testFingerprint)
                        .orElseThrow();

        assertEquals(2L, failureGroup.getOccurrenceCount());
    }
}