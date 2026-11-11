package com.signallens.consumer.repository;

import com.signallens.consumer.model.FailureGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface FailureGroupRepository extends JpaRepository<FailureGroupEntity, Long>,
        JpaSpecificationExecutor<FailureGroupEntity> {

    Optional<FailureGroupEntity> findByFingerprint(String fingerprint);

    @Modifying
    @Query(value = """
            INSERT INTO failure_groups (
                fingerprint,
                service_name,
                environment,
                exception_type,
                normalized_message,
                status,
                first_seen_at,
                last_seen_at,
                occurrence_count
            )
            VALUES (
                :fingerprint,
                :serviceName,
                :environment,
                :exceptionType,
                :normalizedMessage,
                'OPEN',
                :firstSeenAt,
                :lastSeenAt,
                0
            )
            ON CONFLICT (fingerprint) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("fingerprint") String fingerprint,
            @Param("serviceName") String serviceName,
            @Param("environment") String environment,
            @Param("exceptionType") String exceptionType,
            @Param("normalizedMessage") String normalizedMessage,
            @Param("firstSeenAt") OffsetDateTime firstSeenAt,
            @Param("lastSeenAt") OffsetDateTime lastSeenAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE failure_groups
        SET occurrence_count = occurrence_count + 1,
            last_seen_at = GREATEST(last_seen_at, :lastSeenAt),
            first_seen_at = LEAST(first_seen_at, :lastSeenAt),
            status = CASE
                WHEN status = 'RESOLVED' THEN 'OPEN'
                ELSE status
            END
        WHERE id = :id
        """, nativeQuery = true)
    int updateAfterOccurrence(
            @Param("id") Long id,
            @Param("lastSeenAt") OffsetDateTime lastSeenAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE failure_groups
            SET status = :newStatus
            WHERE id = :id
              AND status = :expectedStatus
            """, nativeQuery = true)
    int updateStatus(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus
    );

}