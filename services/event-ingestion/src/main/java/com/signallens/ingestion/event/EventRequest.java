package com.signallens.ingestion.event;

import com.signallens.ingestion.validation.MetadataMaxDepth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.annotation.JsonDeserialize;
import com.signallens.ingestion.validation.OffsetDateTimeStringDeserializer;

public record EventRequest(

        @NotBlank(message = "eventId must not be blank")
        @Size(max = 100, message = "eventId must not exceed 100 characters")
        String eventId,

        @NotBlank(message = "serviceName must not be blank")
        @Size(max = 100, message = "serviceName must not exceed 100 characters")
        String serviceName,

        @NotNull(message = "environment is required")
        Environment environment,

        @JsonDeserialize(using = OffsetDateTimeStringDeserializer.class)
        @NotNull
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
        @MetadataMaxDepth(5)
        Map<String, Object> metadata

) {
    public EventRequest {
        eventId = normalizeRequired(eventId);
        serviceName = normalizeRequired(serviceName);
        message = normalizeRequired(message);

        exceptionType = normalizeOptional(exceptionType);
        traceId = normalizeOptional(traceId);
    }

    private static String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public enum Environment {
        DEVELOPMENT,
        STAGING,
        PRODUCTION
    }

    public enum Severity {
        WARN,
        ERROR,
        FATAL
    }
}