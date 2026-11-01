package com.signallens.ingestion.controller;

import com.signallens.ingestion.event.EventRequest;
import com.signallens.ingestion.mapper.EventMapper;
import com.signallens.ingestion.model.IngestedEvent;
import com.signallens.ingestion.service.EventPublishingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventMapper eventMapper;
    private final EventPublishingService eventPublishingService;

    public EventController(
            EventMapper eventMapper,
            EventPublishingService eventPublishingService) {

        this.eventMapper = eventMapper;
        this.eventPublishingService = eventPublishingService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<Map<String, String>>> ingestEvent(
            @Valid @RequestBody EventRequest request) {

        IngestedEvent event = eventMapper.toIngestedEvent(request);

        return eventPublishingService.publish(event)
                .thenApply(result ->
                        ResponseEntity.accepted().body(
                                Map.of(
                                        "status", "accepted",
                                        "eventId", event.eventId(),
                                        "message", "Event published successfully"
                                )
                        )
                );
    }
}