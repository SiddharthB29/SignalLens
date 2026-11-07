package com.signallens.consumer.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String classification;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    @Column(name = "trace_id")
    private String traceId;

    protected ProcessedEventEntity() {
    }
    public ProcessedEventEntity(
            String eventId,
            String serviceName,
            Environment environment,
            Severity severity,
            String classification,
            String message,
            OffsetDateTime occurredAt,
            OffsetDateTime processedAt,
            String traceId) {

        this.eventId = eventId;
        this.serviceName = serviceName;
        this.environment = environment;
        this.severity = severity;
        this.classification = classification;
        this.message = message;
        this.occurredAt = occurredAt;
        this.processedAt = processedAt;
        this.traceId = traceId;
    }

    // Getters

    public String getEventId() {
        return eventId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getClassification() {
        return classification;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public String getTraceId() {
        return traceId;
    }
}