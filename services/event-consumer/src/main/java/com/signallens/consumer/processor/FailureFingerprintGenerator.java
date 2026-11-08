package com.signallens.consumer.processor;

import com.signallens.consumer.model.IngestedEvent;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

@Component
public class FailureFingerprintGenerator {

    public String generate(IngestedEvent event) {

        Objects.requireNonNull(event, "Event cannot be null");

        String serviceName = Objects.requireNonNull(
                event.serviceName(), "Service name cannot be null"
        );

        String environment = Objects.requireNonNull(
                event.environment(), "Environment cannot be null"
        ).name();

        String exceptionType = event.exceptionType();

        String normalizedMessage = normalizeMessage(
                Objects.requireNonNull(event.message(), "Message cannot be null")
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            updateDigest(digest, serviceName);
            updateDigest(digest, environment);
            updateDigest(digest, exceptionType);
            updateDigest(digest, normalizedMessage);

            return HexFormat.of().formatHex(digest.digest());

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available", exception
            );
        }
    }

    public String normalizeMessage(String message) {

        return message
                .strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("(?U)\\s+", " ");
    }

    private void updateDigest(MessageDigest digest, String value) {

        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES)
                    .putInt(-1)
                    .array());
            return;
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length)
                .array());

        digest.update(bytes);
    }
}