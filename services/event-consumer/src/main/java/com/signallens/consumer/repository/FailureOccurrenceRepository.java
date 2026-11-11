package com.signallens.consumer.repository;

import com.signallens.consumer.model.FailureOccurrenceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface FailureOccurrenceRepository
        extends JpaRepository<FailureOccurrenceEntity, Long> {

    Page<FailureOccurrenceEntity> findByFailureGroup_Id(
            Long failureGroupId,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
            INSERT INTO failure_occurrences (
                failure_group_id,
                event_id,
                occurred_at,
                created_at
            )
            VALUES (
                :failureGroupId,
                :eventId,
                :occurredAt,
                :createdAt
            )
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("failureGroupId") Long failureGroupId,
            @Param("eventId") String eventId,
            @Param("occurredAt") OffsetDateTime occurredAt,
            @Param("createdAt") OffsetDateTime createdAt
    );
}