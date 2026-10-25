package com.signallens.ingestion.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;

public record EventRequest(

        @NotBlank(message = "eventId must not be blank")
        @Size(max = 100, message = "eventId must not exceed 100 characters")
        String eventId,

        @NotBlank(message = "serviceName must not be blank")
        @Size(max = 100, message = "serviceName must not exceed 100 characters")
        String serviceName,

        @NotBlank(message = "environment must not be blank")
        @Size(max = 50, message = "environment must not exceed 50 characters")
        String environment,

        @NotNull(message = "occurredAt is required")
        OffsetDateTime occurredAt,

        @NotNull(message = "severity is required")
        Severity severity,

        @NotBlank(message = "message must not be blank")
        @Size(max = 2000, message = "message must not exceed 2000 characters")
        String message,

        @Size(max = 200, message = "exceptionType must not exceed 200 characters")
        String exceptionType,

        @Size(max = 20000, message = "stackTrace must not exceed 20000 characters")
        String stackTrace,

        @Size(max = 128, message = "traceId must not exceed 128 characters")
        String traceId,

        @Size(max = 50, message = "metadata must not contain more than 50 entries")
        Map<String, Object> metadata

) {
    public enum Severity {
        WARN,
        ERROR,
        FATAL
    }
}