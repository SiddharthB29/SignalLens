package com.signallens.consumer.repository;

import com.signallens.consumer.model.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (
                event_id,
                service_name,
                environment,
                severity,
                classification,
                message,
                occurred_at,
                processed_at,
                trace_id
            )
            VALUES (
                :eventId,
                :serviceName,
                :environment,
                :severity,
                :classification,
                :message,
                :occurredAt,
                :processedAt,
                :traceId
            )
            ON CONFLICT (event_id) DO NOTHING
           """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") String eventId,
            @Param("serviceName") String serviceName,
            @Param("environment") String environment,
            @Param("severity") String severity,
            @Param("classification") String classification,
            @Param("message") String message,
            @Param("occurredAt") java.time.OffsetDateTime occurredAt,
            @Param("processedAt") java.time.OffsetDateTime processedAt,
            @Param("traceId") String traceId
    );
}