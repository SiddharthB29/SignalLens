package com.signallens.consumer.service;

import com.signallens.consumer.dto.FailureGroupResponse;
import com.signallens.consumer.dto.FailureOccurrenceResponse;
import com.signallens.consumer.model.Environment;
import com.signallens.consumer.model.FailureGroupEntity;
import com.signallens.consumer.model.FailureGroupStatus;
import com.signallens.consumer.model.FailureOccurrenceEntity;
import com.signallens.consumer.model.ProcessedEventEntity;
import com.signallens.consumer.repository.FailureGroupRepository;
import com.signallens.consumer.repository.FailureOccurrenceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

@Service
@Transactional(readOnly = true)
public class FailureManagementService {

    private final FailureGroupRepository failureGroupRepository;
    private final FailureOccurrenceRepository failureOccurrenceRepository;

    public FailureManagementService(
            FailureGroupRepository failureGroupRepository,
            FailureOccurrenceRepository failureOccurrenceRepository
    ) {
        this.failureGroupRepository = failureGroupRepository;
        this.failureOccurrenceRepository = failureOccurrenceRepository;
    }

    public Page<FailureGroupResponse> getFailureGroups(
            String status,
            String serviceName,
            String environment,
            Pageable pageable
    ) {
        FailureGroupStatus parsedStatus = parseStatus(status);
        Environment parsedEnvironment = parseEnvironment(environment);

        Specification<FailureGroupEntity> specification =
                (root, query, criteriaBuilder) -> {

                    List<Predicate> predicates = new ArrayList<>();

                    if (parsedStatus != null) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("status"),
                                        parsedStatus
                                )
                        );
                    }

                    if (serviceName != null && !serviceName.isBlank()) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("serviceName"),
                                        serviceName.trim()
                                )
                        );
                    }

                    if (parsedEnvironment != null) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("environment"),
                                        parsedEnvironment
                                )
                        );
                    }

                    return criteriaBuilder.and(
                            predicates.toArray(new Predicate[0])
                    );
                };

        return failureGroupRepository
                .findAll(specification, pageable)
                .map(this::toFailureGroupResponse);
    }

    public FailureGroupResponse getFailureGroupById(Long id) {

        FailureGroupEntity failureGroup = failureGroupRepository
                .findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Failure group not found with ID: " + id
                        )
                );

        return toFailureGroupResponse(failureGroup);
    }

    public Page<FailureOccurrenceResponse> getOccurrences(
            Long failureGroupId,
            Pageable pageable
    ) {

        if (!failureGroupRepository.existsById(failureGroupId)) {
            throw new EntityNotFoundException(
                    "Failure group not found with ID: " + failureGroupId
            );
        }

        return failureOccurrenceRepository
                .findByFailureGroup_Id(failureGroupId, pageable)
                .map(this::toFailureOccurrenceResponse);
    }

    @Transactional
    public FailureGroupResponse acknowledge(Long id) {
        return transitionStatus(
                id,
                Set.of(FailureGroupStatus.OPEN),
                FailureGroupStatus.ACKNOWLEDGED
        );
    }

    @Transactional
    public FailureGroupResponse resolve(Long id) {
        return transitionStatus(
                id,
                Set.of(
                        FailureGroupStatus.OPEN,
                        FailureGroupStatus.ACKNOWLEDGED
                ),
                FailureGroupStatus.RESOLVED
        );
    }

    @Transactional
    public FailureGroupResponse reopen(Long id) {
        return transitionStatus(
                id,
                Set.of(FailureGroupStatus.RESOLVED),
                FailureGroupStatus.OPEN
        );
    }

    private FailureGroupResponse transitionStatus(
            Long id,
            Set<FailureGroupStatus> allowedStatuses,
            FailureGroupStatus newStatus
    ) {
        FailureGroupEntity failureGroup = failureGroupRepository
                .findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Failure group not found with ID: " + id
                        )
                );

        FailureGroupStatus currentStatus = failureGroup.getStatus();

        if (!allowedStatuses.contains(currentStatus)) {
            throw new IllegalStateException(
                    "Cannot change failure group status from "
                            + currentStatus + " to " + newStatus
            );
        }

        int updatedRows = failureGroupRepository.updateStatus(
                id,
                currentStatus.name(),
                newStatus.name()
        );

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Failure group status changed concurrently. "
                            + "Please retry the operation."
            );
        }

        FailureGroupEntity updatedGroup = failureGroupRepository
                .findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Failure group not found after status update: " + id
                        )
                );

        return toFailureGroupResponse(updatedGroup);
    }

    private FailureGroupResponse toFailureGroupResponse(
            FailureGroupEntity entity
    ) {
        return new FailureGroupResponse(
                entity.getId(),
                entity.getFingerprint(),
                entity.getServiceName(),
                entity.getEnvironment().name(),
                entity.getExceptionType(),
                entity.getNormalizedMessage(),
                entity.getStatus().name(),
                entity.getFirstSeenAt(),
                entity.getLastSeenAt(),
                entity.getOccurrenceCount()
        );
    }

    private FailureOccurrenceResponse toFailureOccurrenceResponse(
            FailureOccurrenceEntity occurrence
    ) {
        ProcessedEventEntity event = occurrence.getProcessedEvent();

        return new FailureOccurrenceResponse(
                occurrence.getId(),
                event.getEventId(),
                event.getServiceName(),
                event.getEnvironment().name(),
                event.getSeverity().name(),
                event.getClassification(),
                event.getMessage(),
                occurrence.getOccurredAt(),
                event.getProcessedAt(),
                occurrence.getCreatedAt(),
                event.getTraceId()
        );
    }

    private FailureGroupStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        return FailureGroupStatus.valueOf(
                status.trim().toUpperCase(Locale.ROOT)
        );
    }

    private Environment parseEnvironment(String environment) {
        if (environment == null || environment.isBlank()) {
            return null;
        }

        return Environment.valueOf(
                environment.trim().toUpperCase(Locale.ROOT)
        );
    }
}