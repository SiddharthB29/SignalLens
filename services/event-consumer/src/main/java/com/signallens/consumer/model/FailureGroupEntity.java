package com.signallens.consumer.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "failure_groups")
public class FailureGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "fingerprint",
            nullable = false,
            unique = true,
            length = 64,
            updatable = false
    )
    private String fingerprint;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Environment environment;

    @Column(name = "exception_type", length = 200)
    private String exceptionType;

    @Column(
            name = "normalized_message",
            nullable = false,
            length = 2000
    )
    private String normalizedMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FailureGroupStatus status;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "occurrence_count", nullable = false)
    private Long occurrenceCount;

    protected FailureGroupEntity() {
    }

    public FailureGroupEntity(
            String fingerprint,
            String serviceName,
            Environment environment,
            String exceptionType,
            String normalizedMessage,
            FailureGroupStatus status,
            OffsetDateTime firstSeenAt,
            OffsetDateTime lastSeenAt,
            Long occurrenceCount
    ) {
        this.fingerprint = fingerprint;
        this.serviceName = serviceName;
        this.environment = environment;
        this.exceptionType = exceptionType;
        this.normalizedMessage = normalizedMessage;
        this.status = status;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.occurrenceCount = occurrenceCount;
    }

    public Long getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public String getNormalizedMessage() {
        return normalizedMessage;
    }

    public FailureGroupStatus getStatus() {
        return status;
    }

    public OffsetDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public Long getOccurrenceCount() {
        return occurrenceCount;
    }
}