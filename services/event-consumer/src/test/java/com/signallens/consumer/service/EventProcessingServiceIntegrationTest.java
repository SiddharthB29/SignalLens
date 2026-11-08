package com.signallens.consumer.service;

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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
class EventProcessingServiceIntegrationTest {

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private FailureGroupRepository failureGroupRepository;

    @Autowired
    private FailureFingerprintGenerator fingerprintGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String testEventId;
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

        if (testFingerprint != null) {
            failureGroupRepository.findByFingerprint(testFingerprint)
                    .ifPresent(group ->
                            failureGroupRepository.deleteById(group.getId())
                    );
        }
    }
}