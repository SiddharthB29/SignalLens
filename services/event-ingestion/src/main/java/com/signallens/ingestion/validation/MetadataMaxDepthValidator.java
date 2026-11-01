package com.signallens.ingestion.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.Map;

public class MetadataMaxDepthValidator
        implements ConstraintValidator<MetadataMaxDepth, Map<String, Object>> {

    private int maxDepth;

    @Override
    public void initialize(MetadataMaxDepth annotation) {
        this.maxDepth = annotation.value();
    }

    @Override
    public boolean isValid(
            Map<String, Object> metadata,
            ConstraintValidatorContext context) {

        if (metadata == null) {
            return true;
        }

        return hasValidDepth(metadata, 1);
    }

    private boolean hasValidDepth(Object value, int depth) {
        if (depth > maxDepth) {
            return false;
        }

        if (value instanceof Map<?, ?> map) {
            for (Object nestedValue : map.values()) {
                if (isNestedContainer(nestedValue)
                        && !hasValidDepth(nestedValue, depth + 1)) {
                    return false;
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object nestedValue : list) {
                if (isNestedContainer(nestedValue)
                        && !hasValidDepth(nestedValue, depth + 1)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isNestedContainer(Object value) {
        return value instanceof Map<?, ?> || value instanceof List<?>;
    }
}