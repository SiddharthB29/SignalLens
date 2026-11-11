package com.signallens.consumer.dto;

import java.time.OffsetDateTime;

public record FailureOccurrenceResponse(
        Long occurrenceId,
        String eventId,
        String serviceName,
        String environment,
        String severity,
        String classification,
        String message,
        OffsetDateTime occurredAt,
        OffsetDateTime processedAt,
        OffsetDateTime createdAt,
        String traceId
) {
}
