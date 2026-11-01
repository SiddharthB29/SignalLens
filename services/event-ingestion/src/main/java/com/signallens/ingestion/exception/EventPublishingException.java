package com.signallens.ingestion.exception;

public class EventPublishingException extends RuntimeException {

    public EventPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}