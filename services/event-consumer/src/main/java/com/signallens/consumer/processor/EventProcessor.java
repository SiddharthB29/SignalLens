package com.signallens.consumer.processor;

import com.signallens.consumer.model.IngestedEvent;
import com.signallens.consumer.model.ProcessedEvent;
import com.signallens.consumer.model.Severity;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class EventProcessor {

    public ProcessedEvent process(IngestedEvent event) {

        System.out.println("Event Processor: Processing event details");

        String classification = switch (event.severity()) {
            case WARN -> "Warning";
            case ERROR -> "Application Error";
            case FATAL -> "Critical Failure";
        };

        ProcessedEvent processedEvent = new ProcessedEvent(
                event.eventId(),
                event.serviceName(),
                event.environment(),
                event.severity(),
                classification,
                event.message(),
                event.occurredAt(),
                OffsetDateTime.now(),
                event.traceId()
        );

        System.out.println("Processed Event: " + processedEvent);

        return processedEvent;
    }
}