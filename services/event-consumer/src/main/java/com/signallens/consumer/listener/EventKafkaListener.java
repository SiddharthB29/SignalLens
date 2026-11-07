package com.signallens.consumer.listener;

import com.signallens.consumer.model.IngestedEvent;
import com.signallens.consumer.model.ProcessedEvent;
import com.signallens.consumer.service.EventProcessingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventKafkaListener {

    private final EventProcessingService eventProcessingService;

    public EventKafkaListener(EventProcessingService eventProcessingService) {
        this.eventProcessingService = eventProcessingService;
    }

    @KafkaListener(topics = "${signallens.kafka.topic}")
    public void consume(IngestedEvent event) {

        ProcessedEvent processedEvent = eventProcessingService.process(event);

        System.out.println("Final Processed Event: " + processedEvent);

    }
}