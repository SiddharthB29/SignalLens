package com.signallens.ingestion.service;

import com.signallens.ingestion.event.EventRequest;
import com.signallens.ingestion.model.IngestedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class EventPublishingServiceIntegrationTest {

    private static final String TOPIC = "signallens.events.test";

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer("apache/kafka:4.1.1");

    @Test

    void shouldPublishEventToKafkaSuccessfully() throws Exception {
        try (AdminClient adminClient = AdminClient.create(
                Map.of(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                        kafka.getBootstrapServers()
                )
        )) {
            NewTopic topic = new NewTopic(TOPIC, 1, (short) 1);

            adminClient.createTopics(List.of(topic)).all().get();
        }

        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers()
        );

        DefaultKafkaProducerFactory<String, IngestedEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(
                        producerProperties,
                        new org.apache.kafka.common.serialization.StringSerializer(),
                        new JacksonJsonSerializer<>()
                );

        try {
            KafkaTemplate<String, IngestedEvent> kafkaTemplate =
                    new KafkaTemplate<>(producerFactory);

            EventPublishingService publishingService =
                    new EventPublishingService(kafkaTemplate, TOPIC);

            Map<String, Object> metadata = Map.of(
                    "source", "integration-test",
                    "attempt", 1
            );

            OffsetDateTime occurredAt = OffsetDateTime.now();

            IngestedEvent event = new IngestedEvent(
                    "integration-test-001",
                    "payment-service",
                    EventRequest.Environment.PRODUCTION,
                    occurredAt,
                    EventRequest.Severity.ERROR,
                    "Testing real Kafka integration",
                    "IllegalStateException",
                    "Test stack trace",
                    "trace-001",
                    metadata
            );

            publishingService.publish(event).join();

            Map<String, Object> consumerProperties = new HashMap<>();
            consumerProperties.put(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    kafka.getBootstrapServers()
            );
            consumerProperties.put(
                    ConsumerConfig.GROUP_ID_CONFIG,
                    UUID.randomUUID().toString()
            );
            consumerProperties.put(
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                    "earliest"
            );
            consumerProperties.put(
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                    StringDeserializer.class
            );
            consumerProperties.put(
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                    JacksonJsonDeserializer.class
            );
            consumerProperties.put(
                    JacksonJsonDeserializer.VALUE_DEFAULT_TYPE,
                    IngestedEvent.class.getName()
            );
            consumerProperties.put(
                    JacksonJsonDeserializer.TRUSTED_PACKAGES,
                    "com.signallens.ingestion.model"
            );

            try (KafkaConsumer<String, IngestedEvent> consumer =
                         new KafkaConsumer<>(consumerProperties)) {

                consumer.subscribe(List.of(TOPIC));

                ConsumerRecord<String, IngestedEvent> receivedRecord = null;
                long deadline = System.nanoTime()
                        + Duration.ofSeconds(10).toNanos();

                while (System.nanoTime() < deadline
                        && receivedRecord == null) {

                    var records = consumer.poll(Duration.ofMillis(500));

                    if (!records.isEmpty()) {
                        receivedRecord = records.iterator().next();
                    }
                }

                assertNotNull(
                        receivedRecord,
                        "Kafka should receive the event"
                );

                assertEquals(event.eventId(), receivedRecord.key());

                IngestedEvent receivedEvent = receivedRecord.value();

                assertNotNull(receivedEvent);

                assertEquals(event.eventId(), receivedEvent.eventId());
                assertEquals(event.serviceName(), receivedEvent.serviceName());
                assertEquals(event.environment(), receivedEvent.environment());
                assertEquals(
                        event.occurredAt().toInstant(),
                        receivedEvent.occurredAt().toInstant()
                );
                assertEquals(event.severity(), receivedEvent.severity());
                assertEquals(event.message(), receivedEvent.message());
                assertEquals(event.exceptionType(), receivedEvent.exceptionType());
                assertEquals(event.stackTrace(), receivedEvent.stackTrace());
                assertEquals(event.traceId(), receivedEvent.traceId());
                assertEquals(event.metadata(), receivedEvent.metadata());
            }

        } finally {
            producerFactory.destroy();
        }
    }
}