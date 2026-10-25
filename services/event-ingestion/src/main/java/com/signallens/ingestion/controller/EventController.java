package com.signallens.ingestion.controller;

import com.signallens.ingestion.event.EventRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    @PostMapping
    public ResponseEntity<Map<String, String>> ingestEvent(
            @Valid @RequestBody EventRequest request) {

        return ResponseEntity.ok(
                Map.of(
                        "status", "validated",
                        "eventId", request.eventId(),
                        "message", "Event request validated successfully"
                )
        );
    }
}