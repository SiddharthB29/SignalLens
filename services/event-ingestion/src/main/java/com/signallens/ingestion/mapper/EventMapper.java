package com.signallens.ingestion.mapper;

import com.signallens.ingestion.event.EventRequest;
import com.signallens.ingestion.model.IngestedEvent;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public IngestedEvent toIngestedEvent(EventRequest request) {

        return new IngestedEvent(
                request.eventId(),
                request.serviceName(),
                request.environment(),
                request.occurredAt(),
                request.severity(),
                request.message(),
                request.exceptionType(),
                request.stackTrace(),
                request.traceId(),
                request.metadata()
        );
    }
}