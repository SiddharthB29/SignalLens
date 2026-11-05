package com.signallens.consumer.listener;

import com.signallens.consumer.model.IngestedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventKafkaListener {

    @KafkaListener(topics = "${signallens.kafka.topic}")
    public void consume(IngestedEvent event) {

        System.out.println("Received event from Kafka:");
        System.out.println(event);

    }

}