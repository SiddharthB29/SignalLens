package com.signallens.consumer.service;

import com.signallens.consumer.model.IngestedEvent;
import com.signallens.consumer.model.ProcessedEvent;
import com.signallens.consumer.model.ProcessedEventEntity;
import com.signallens.consumer.processor.EventProcessor;
import com.signallens.consumer.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventProcessingService {

    private final EventProcessor eventProcessor;
    private final ProcessedEventRepository processedEventRepository;

    public EventProcessingService(
            EventProcessor eventProcessor,
            ProcessedEventRepository processedEventRepository) {

        this.eventProcessor = eventProcessor;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public ProcessedEvent process(IngestedEvent event) {

        System.out.println("Event Processing Service: Received event");

        // First check: avoid unnecessary processing.
        if (processedEventRepository.existsById(event.eventId())) {

            System.out.println(
                    "Duplicate event detected. Skipping processing: "
                            + event.eventId()
            );

            return getExistingProcessedEvent(event.eventId());
        }

        // Process only events that are not already persisted.
        ProcessedEvent processedEvent = eventProcessor.process(event);

        ProcessedEventEntity entity = new ProcessedEventEntity(
                processedEvent.eventId(),
                processedEvent.serviceName(),
                processedEvent.environment(),
                processedEvent.severity(),
                processedEvent.classification(),
                processedEvent.message(),
                processedEvent.occurredAt(),
                processedEvent.processedAt(),
                processedEvent.traceId()
        );

        // Second protection: atomic insert handles concurrent duplicates.
        int insertedRows = processedEventRepository.insertIfAbsent(
                entity.getEventId(),
                entity.getServiceName(),
                entity.getEnvironment().name(),
                entity.getSeverity().name(),
                entity.getClassification(),
                entity.getMessage(),
                entity.getOccurredAt(),
                entity.getProcessedAt(),
                entity.getTraceId()
        );

        if (insertedRows == 0) {

            System.out.println(
                    "Concurrent duplicate detected: " + event.eventId()
            );

            return getExistingProcessedEvent(event.eventId());
        }

        System.out.println("Event Processing Service: Processing completed");

        return processedEvent;
    }

    private ProcessedEvent getExistingProcessedEvent(String eventId) {

        ProcessedEventEntity existingEntity =
                processedEventRepository.findById(eventId)
                        .orElseThrow();

        return new ProcessedEvent(
                existingEntity.getEventId(),
                existingEntity.getServiceName(),
                existingEntity.getEnvironment(),
                existingEntity.getSeverity(),
                existingEntity.getClassification(),
                existingEntity.getMessage(),
                existingEntity.getOccurredAt(),
                existingEntity.getProcessedAt(),
                existingEntity.getTraceId()
        );
    }
}