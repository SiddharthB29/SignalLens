package com.signallens.consumer.service;

import com.signallens.consumer.model.FailureGroupEntity;
import com.signallens.consumer.model.IngestedEvent;
import com.signallens.consumer.repository.FailureGroupRepository;
import com.signallens.consumer.repository.FailureOccurrenceRepository;
import com.signallens.consumer.processor.FailureFingerprintGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class FailureGroupingService {

    private final FailureFingerprintGenerator fingerprintGenerator;
    private final FailureGroupRepository failureGroupRepository;
    private final FailureOccurrenceRepository failureOccurrenceRepository;

    public FailureGroupingService(
            FailureFingerprintGenerator fingerprintGenerator,
            FailureGroupRepository failureGroupRepository,
            FailureOccurrenceRepository failureOccurrenceRepository
    ) {
        this.fingerprintGenerator = fingerprintGenerator;
        this.failureGroupRepository = failureGroupRepository;
        this.failureOccurrenceRepository = failureOccurrenceRepository;
    }

    @Transactional
    public void group(IngestedEvent event) {

        // Step 1: Generate the failure fingerprint.
        String fingerprint = fingerprintGenerator.generate(event);

        // Step 2: Normalize the original message.
        String normalizedMessage =
                fingerprintGenerator.normalizeMessage(event.message());

        // Step 3: Create the failure group if it does not exist.
        failureGroupRepository.insertIfAbsent(
                fingerprint,
                event.serviceName(),
                event.environment().name(),
                event.exceptionType(),
                normalizedMessage,
                event.occurredAt(),
                event.occurredAt()
        );

        // Step 4: Retrieve the existing or newly created group.
        FailureGroupEntity failureGroup =
                failureGroupRepository.findByFingerprint(fingerprint)
                        .orElseThrow(() -> new IllegalStateException(
                                "Failure group could not be found: " + fingerprint
                        ));

        // Step 5: Attempt to insert the individual occurrence.
        int insertedRows = failureOccurrenceRepository.insertIfAbsent(
                failureGroup.getId(),
                event.eventId(),
                event.occurredAt(),
                OffsetDateTime.now()
        );

        // Step 6: Update the group only if a new occurrence was inserted.
        if (insertedRows == 1) {

            int updatedRows = failureGroupRepository.updateAfterOccurrence(
                    failureGroup.getId(),
                    event.occurredAt()
            );

            if (updatedRows != 1) {
                throw new IllegalStateException(
                        "Failure group update failed: " + failureGroup.getId()
                );
            }
        }
    }
}