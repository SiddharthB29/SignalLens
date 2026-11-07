package com.signallens.consumer.model;

import java.time.OffsetDateTime;

public record ProcessedEvent(

        String eventId,

        String serviceName,

        Environment environment,

        Severity severity,

        String classification,

        String message,

        OffsetDateTime occurredAt,

        OffsetDateTime processedAt,

        String traceId

) {
}