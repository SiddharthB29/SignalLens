package com.signallens.consumer.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record IngestedEvent(

        String eventId,

        String serviceName,

        Environment environment,

        OffsetDateTime occurredAt,

        Severity severity,

        String message,

        String exceptionType,

        String stackTrace,

        String traceId,

        Map<String, Object> metadata

) {
}