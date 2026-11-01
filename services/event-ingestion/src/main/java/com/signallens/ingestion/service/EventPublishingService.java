package com.signallens.ingestion.service;

import com.signallens.ingestion.model.IngestedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.signallens.ingestion.exception.EventPublishingException;
import java.util.concurrent.CompletionException;

import java.util.concurrent.CompletableFuture;

@Service
public class EventPublishingService {

    private final KafkaTemplate<String, IngestedEvent> kafkaTemplate;
    private final String topic;

    public EventPublishingService(
            KafkaTemplate<String, IngestedEvent> kafkaTemplate,
            @Value("${signallens.kafka.topic}") String topic) {

        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<Void> publish(IngestedEvent event) {

        return kafkaTemplate
                .send(topic, event.eventId(), event)
                .handle((result, exception) -> {

                    if (exception != null) {
                        throw new CompletionException(
                                new EventPublishingException(
                                        "Failed to publish event to Kafka",
                                        exception
                                )
                        );
                    }

                    return null;
                });
    }
}