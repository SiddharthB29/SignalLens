package com.signallens.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.signallens.ingestion.service.EventPublishingService;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class EventIngestionApplicationTests {

	@Autowired
	private EventPublishingService eventPublishingService;

	@Test
	void contextLoads() {
	}

	@Test
	void eventPublishingServiceShouldBeAvailable() {
		assertNotNull(eventPublishingService);
	}
}