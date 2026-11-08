package com.signallens.consumer.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "failure_occurrences")
public class FailureOccurrenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "failure_group_id",
            nullable = false
    )
    private FailureGroupEntity failureGroup;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "event_id",
            nullable = false,
            unique = true
    )
    private ProcessedEventEntity processedEvent;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected FailureOccurrenceEntity() {
    }

    public FailureOccurrenceEntity(
            FailureGroupEntity failureGroup,
            ProcessedEventEntity processedEvent,
            OffsetDateTime occurredAt,
            OffsetDateTime createdAt
    ) {
        this.failureGroup = failureGroup;
        this.processedEvent = processedEvent;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public FailureGroupEntity getFailureGroup() {
        return failureGroup;
    }

    public ProcessedEventEntity getProcessedEvent() {
        return processedEvent;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}