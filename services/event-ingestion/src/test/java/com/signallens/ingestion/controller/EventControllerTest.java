package com.signallens.ingestion.controller;

import com.signallens.ingestion.exception.EventPublishingException;
import com.signallens.ingestion.model.IngestedEvent;
import com.signallens.ingestion.service.EventPublishingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.signallens.ingestion.mapper.EventMapper;

@WebMvcTest(EventController.class)
@Import(EventMapper.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventPublishingService publishingService;

    @Test
    void shouldReturnAcceptedWhenEventIsPublished() throws Exception {

        when(publishingService.publish(any(IngestedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        var mvcResult = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createValidRequest()))
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.eventId").value("evt-1001"))
                .andExpect(jsonPath("$.message")
                        .value("Event published successfully"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenPublishingFails() throws Exception {

        when(publishingService.publish(any(IngestedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new EventPublishingException(
                                "Failed to publish event to Kafka",
                                new RuntimeException("Kafka publishing failed")
                        )
                ));

        var mvcResult = mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createValidRequest()))
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message")
                        .value("Event could not be published. Please try again later."));
    }

    private String createValidRequest() {

        return """
                {
                    "eventId": "evt-1001",
                    "serviceName": "payment-service",
                    "environment": "PRODUCTION",
                    "occurredAt": "2026-11-01T14:30:00+05:30",
                    "severity": "ERROR",
                    "message": "Payment processing failed",
                    "exceptionType": "NullPointerException",
                    "stackTrace": "PaymentService.processPayment()",
                    "traceId": "trace-123",
                    "metadata": {
                        "paymentId": "PAY-100"
                    }
                }
                """;
    }
}