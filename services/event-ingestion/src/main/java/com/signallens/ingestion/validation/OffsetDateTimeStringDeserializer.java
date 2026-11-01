package com.signallens.ingestion.validation;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class OffsetDateTimeStringDeserializer
        extends ValueDeserializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(
            JsonParser parser,
            DeserializationContext context) {

        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(
                    OffsetDateTime.class,
                    "occurredAt must be an ISO-8601 timestamp string with a timezone offset"
            );
        }

        String value = parser.getString();

        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            return context.reportInputMismatch(
                    OffsetDateTime.class,
                    "occurredAt must be a valid ISO-8601 timestamp with a timezone offset"
            );
        }
    }
}