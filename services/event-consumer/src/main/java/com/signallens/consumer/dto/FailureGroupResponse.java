package com.signallens.consumer.dto;

import java.time.OffsetDateTime;

public record FailureGroupResponse(
        Long id,
        String fingerprint,
        String serviceName,
        String environment,
        String exceptionType,
        String normalizedMessage,
        String status,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt,
        Long occurrenceCount
) {
}