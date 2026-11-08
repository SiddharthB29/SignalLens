package com.signallens.consumer.processor;

import com.signallens.consumer.model.Environment;
import com.signallens.consumer.model.IngestedEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import com.signallens.consumer.model.Severity;

import static org.junit.jupiter.api.Assertions.*;

class FailureFingerprintGeneratorTest {

    private final FailureFingerprintGenerator generator =
            new FailureFingerprintGenerator();

    @Test
    void identicalEventsShouldProduceSameFingerprint() {

        IngestedEvent first = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment processing failed"
        );

        IngestedEvent second = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment processing failed"
        );

        assertEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }

    @Test
    void messageCaseDifferencesShouldProduceSameFingerprint() {

        IngestedEvent first = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment Processing Failed"
        );

        IngestedEvent second = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "payment processing failed"
        );

        assertEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }

    @Test
    void messageWhitespaceDifferencesShouldProduceSameFingerprint() {

        IngestedEvent first = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "  Payment   processing\nfailed  "
        );

        IngestedEvent second = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment processing failed"
        );

        assertEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }

    @Test
    void differentServiceNamesShouldProduceDifferentFingerprints() {

        IngestedEvent first = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment failed"
        );

        IngestedEvent second = createEvent(
                "order-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment failed"
        );

        assertNotEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }

    @Test
    void differentEnvironmentsShouldProduceDifferentFingerprints() {

        IngestedEvent first = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment failed"
        );

        IngestedEvent second = createEvent(
                "payment-service",
                Environment.STAGING,
                "NullPointerException",
                "Payment failed"
        );

        assertNotEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }

    @Test
    void differentExceptionTypesShouldProduceDifferentFingerprints() {

        IngestedEvent first = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment failed"
        );

        IngestedEvent second = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "IllegalStateException",
                "Payment failed"
        );

        assertNotEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }

    @Test
    void nullExceptionTypesShouldProduceSameFingerprint() {

        IngestedEvent first = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                null,
                "Payment failed"
        );

        IngestedEvent second = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                null,
                "Payment failed"
        );

        assertEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }

    @Test
    void nullAndNonNullExceptionTypesShouldProduceDifferentFingerprints() {

        IngestedEvent first = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                null,
                "Payment failed"
        );

        IngestedEvent second = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment failed"
        );

        assertNotEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }

    @Test
    void punctuationAndIdentifiersShouldBePreserved() {

        IngestedEvent first = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "RuntimeException",
                "Payment failed: transaction #12345"
        );

        IngestedEvent second = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "RuntimeException",
                "Payment failed transaction #12345"
        );

        assertNotEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }

    @Test
    void fingerprintShouldContain64HexadecimalCharacters() {

        IngestedEvent event = createEvent(
                "payment-service",
                Environment.PRODUCTION,
                "NullPointerException",
                "Payment failed"
        );

        String fingerprint = generator.generate(event);

        assertEquals(64, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }

    private IngestedEvent createEvent(
            String serviceName,
            Environment environment,
            String exceptionType,
            String message
    ) {

        return new IngestedEvent(
                "event-1",
                serviceName,
                environment,
                null,
                null,
                message,
                exceptionType,
                null,
                null,
                null
        );
    }

    @Test
    void excludedFieldsShouldNotAffectFingerprint() {

        IngestedEvent first = new IngestedEvent(
                "event-1",
                "payment-service",
                Environment.PRODUCTION,
                OffsetDateTime.parse("2026-11-08T10:00:00+05:30"),
                Severity.ERROR,
                "Payment processing failed",
                "NullPointerException",
                "Stack trace one",
                "trace-111",
                Map.of("requestId", "123")
        );

        IngestedEvent second = new IngestedEvent(
                "event-2",
                "payment-service",
                Environment.PRODUCTION,
                OffsetDateTime.parse("2026-11-08T11:00:00+05:30"),
                Severity.FATAL,
                "Payment processing failed",
                "NullPointerException",
                "Stack trace two",
                "trace-222",
                Map.of("requestId", "456")
        );

        assertEquals(
                generator.generate(first),
                generator.generate(second)
        );
    }
}
