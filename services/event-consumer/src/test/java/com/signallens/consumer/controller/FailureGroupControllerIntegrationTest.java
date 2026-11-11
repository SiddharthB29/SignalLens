package com.signallens.consumer.controller;

import tools.jackson.databind.JsonNode;
import com.signallens.consumer.model.Environment;
import com.signallens.consumer.model.FailureGroupEntity;
import com.signallens.consumer.model.IngestedEvent;
import com.signallens.consumer.model.Severity;
import com.signallens.consumer.processor.FailureFingerprintGenerator;
import com.signallens.consumer.repository.FailureGroupRepository;
import com.signallens.consumer.service.EventProcessingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.listener.auto-startup=false"
        }
)
@AutoConfigureTestRestTemplate
class FailureGroupControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventProcessingService eventProcessingService;

    @Autowired
    private FailureGroupRepository failureGroupRepository;

    @Autowired
    private FailureFingerprintGenerator fingerprintGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> testEventIds = new ArrayList<>();
    private final List<String> testFingerprints = new ArrayList<>();

    @Test
    void shouldRetrieveFailureGroupsWithFiltersAndPagination() {

        // Create two different failure groups.
        FailureGroupEntity paymentGroup = createFailureGroup(
                "payment-service",
                Environment.PRODUCTION,
                "Payment processing failed",
                "NullPointerException"
        );

        createFailureGroup(
                "inventory-service",
                Environment.STAGING,
                "Inventory lookup failed",
                "IllegalArgumentException"
        );

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups"
                        + "?status=OPEN"
                        + "&serviceName=payment-service"
                        + "&environment=PRODUCTION"
                        + "&page=0"
                        + "&size=10"
                        + "&sort=lastSeenAt,desc",
                JsonNode.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        JsonNode content = response.getBody().path("content");

        assertEquals(1, content.size());
        assertEquals(
                paymentGroup.getId(),
                content.get(0).path("id").asLong()
        );
        assertEquals(
                "payment-service",
                content.get(0).path("serviceName").asText()
        );
    }

    @Test
    void shouldRetrieveFailureGroupAndItsOccurrences() {

        FailureGroupEntity group = createFailureGroup(
                "payment-service",
                Environment.PRODUCTION,
                "Payment processing failed",
                "NullPointerException"
        );

        ResponseEntity<JsonNode> groupResponse = restTemplate.getForEntity(
                "/api/failure-groups/" + group.getId(),
                JsonNode.class
        );

        assertEquals(HttpStatus.OK, groupResponse.getStatusCode());
        assertNotNull(groupResponse.getBody());

        assertEquals(
                group.getId(),
                groupResponse.getBody().path("id").asLong()
        );

        ResponseEntity<JsonNode> occurrenceResponse =
                restTemplate.getForEntity(
                        "/api/failure-groups/" + group.getId()
                                + "/occurrences?page=0&size=10",
                        JsonNode.class
                );

        assertEquals(HttpStatus.OK, occurrenceResponse.getStatusCode());
        assertNotNull(occurrenceResponse.getBody());

        JsonNode occurrences =
                occurrenceResponse.getBody().path("content");

        assertEquals(1, occurrences.size());
        assertEquals(
                testEventIds.get(0),
                occurrences.get(0).path("eventId").asText()
        );
        ResponseEntity<JsonNode> sortedOccurrenceResponse =
                restTemplate.getForEntity(
                        "/api/failure-groups/" + group.getId()
                                + "/occurrences?page=0&size=10&sort=occurredAt,asc",
                        JsonNode.class
                );

        assertEquals(
                HttpStatus.OK,
                sortedOccurrenceResponse.getStatusCode()
        );

        assertNotNull(sortedOccurrenceResponse.getBody());

        JsonNode sortedOccurrences =
                sortedOccurrenceResponse.getBody().path("content");

        assertEquals(1, sortedOccurrences.size());

        assertEquals(
                testEventIds.get(0),
                sortedOccurrences.get(0).path("eventId").asText()
        );
    }

    @Test
    void shouldSupportFailureGroupStatusTransitions() {

        FailureGroupEntity group = createFailureGroup(
                "payment-service",
                Environment.PRODUCTION,
                "Payment processing failed",
                "NullPointerException"
        );

        ResponseEntity<JsonNode> acknowledgeResponse = restTemplate.exchange(
                "/api/failure-groups/" + group.getId() + "/acknowledge",
                HttpMethod.PATCH,
                HttpEntity.EMPTY,
                JsonNode.class
        );

        assertEquals(HttpStatus.OK, acknowledgeResponse.getStatusCode());
        assertEquals(
                "ACKNOWLEDGED",
                acknowledgeResponse.getBody().path("status").asText()
        );

        ResponseEntity<JsonNode> resolveResponse = restTemplate.exchange(
                "/api/failure-groups/" + group.getId() + "/resolve",
                HttpMethod.PATCH,
                HttpEntity.EMPTY,
                JsonNode.class
        );

        assertEquals(HttpStatus.OK, resolveResponse.getStatusCode());
        assertEquals(
                "RESOLVED",
                resolveResponse.getBody().path("status").asText()
        );

        ResponseEntity<JsonNode> reopenResponse = restTemplate.exchange(
                "/api/failure-groups/" + group.getId() + "/reopen",
                HttpMethod.PATCH,
                HttpEntity.EMPTY,
                JsonNode.class
        );

        assertEquals(HttpStatus.OK, reopenResponse.getStatusCode());
        assertEquals(
                "OPEN",
                reopenResponse.getBody().path("status").asText()
        );
    }

    @Test
    void shouldReturnNotFoundForMissingFailureGroup() {

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups/999999999",
                JsonNode.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForInvalidStatusFilter() {

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups?status=UNKNOWN",
                JsonNode.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForNegativePageNumber() {

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups?page=-1&size=5",
                JsonNode.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForZeroPageSize() {

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups?page=0&size=0",
                JsonNode.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForExcessivePageSize() {

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups?page=0&size=101",
                JsonNode.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForInvalidFailureGroupSortField() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups?sort=invalidField,asc",
                JsonNode.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForMultipleSortProperties() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups?sort=lastSeenAt,desc&sort=serviceName,asc",
                JsonNode.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForInvalidSortDirection() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups?sort=lastSeenAt,invalid",
                JsonNode.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnBadRequestForInvalidOccurrenceSortField() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "/api/failure-groups/1/occurrences?sort=invalidField,asc",
                JsonNode.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shouldReturnConflictForInvalidStatusTransition() {

        FailureGroupEntity group = createFailureGroup(
                "payment-service",
                Environment.PRODUCTION,
                "Payment processing failed",
                "NullPointerException"
        );

        // Resolve the group first.
        restTemplate.exchange(
                "/api/failure-groups/" + group.getId() + "/resolve",
                HttpMethod.PATCH,
                HttpEntity.EMPTY,
                JsonNode.class
        );

        // A resolved group cannot be acknowledged directly.
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/failure-groups/" + group.getId() + "/acknowledge",
                HttpMethod.PATCH,
                HttpEntity.EMPTY,
                JsonNode.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    private FailureGroupEntity createFailureGroup(
            String serviceName,
            Environment environment,
            String message,
            String exceptionType
    ) {

        String eventId = "api-test-" + UUID.randomUUID();

        IngestedEvent event = new IngestedEvent(
                eventId,
                serviceName,
                environment,
                OffsetDateTime.now(),
                Severity.ERROR,
                message,
                exceptionType,
                "API integration test stack trace",
                "api-trace-" + UUID.randomUUID(),
                null
        );

        String fingerprint = fingerprintGenerator.generate(event);

        testEventIds.add(eventId);
        testFingerprints.add(fingerprint);

        eventProcessingService.process(event);

        return failureGroupRepository.findByFingerprint(fingerprint)
                .orElseThrow();
    }

    @AfterEach
    void cleanUpTestData() {

        for (String eventId : testEventIds) {
            jdbcTemplate.update(
                    "DELETE FROM failure_occurrences WHERE event_id = ?",
                    eventId
            );

            jdbcTemplate.update(
                    "DELETE FROM processed_events WHERE event_id = ?",
                    eventId
            );
        }

        for (String fingerprint : testFingerprints) {
            jdbcTemplate.update(
                    "DELETE FROM failure_groups WHERE fingerprint = ?",
                    fingerprint
            );
        }
    }
}