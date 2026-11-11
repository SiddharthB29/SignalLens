package com.signallens.consumer.validation;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PaginationValidator {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> FAILURE_GROUP_SORT_FIELDS = Set.of(
            "id",
            "serviceName",
            "environment",
            "status",
            "firstSeenAt",
            "lastSeenAt",
            "occurrenceCount"
    );

    private static final Set<String> OCCURRENCE_SORT_FIELDS = Set.of(
            "id",
            "occurredAt",
            "createdAt"
    );

    public void validate(int page, int size) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative."
            );
        }

        if (size < 1) {
            throw new IllegalArgumentException(
                    "Page size must be at least 1."
            );
        }

        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size cannot exceed 100."
            );
        }
    }

    public void validateSorting(
            Pageable pageable,
            Set<String> allowedFields
    ) {

        if (pageable.getSort().stream().count() > 1) {
            throw new IllegalArgumentException(
                    "Only one sorting property is allowed."
            );
        }

        pageable.getSort().forEach(order -> {

            if (!allowedFields.contains(order.getProperty())) {
                throw new IllegalArgumentException(
                        "Sorting by '" + order.getProperty()
                                + "' is not supported."
                );
            }
        });
    }

    public Set<String> getFailureGroupSortFields() {
        return FAILURE_GROUP_SORT_FIELDS;
    }

    public Set<String> getOccurrenceSortFields() {
        return OCCURRENCE_SORT_FIELDS;
    }
}