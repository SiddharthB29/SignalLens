package com.signallens.ingestion.model;

import com.signallens.ingestion.event.EventRequest;

import java.time.OffsetDateTime;
import java.util.Map;

public record IngestedEvent(

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
}