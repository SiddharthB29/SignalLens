package com.signallens.consumer.controller;

import com.signallens.consumer.dto.FailureGroupResponse;
import com.signallens.consumer.dto.FailureOccurrenceResponse;
import com.signallens.consumer.service.FailureManagementService;
import com.signallens.consumer.validation.PaginationValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/failure-groups")
public class FailureGroupController {

    private final FailureManagementService failureManagementService;
    private final PaginationValidator paginationValidator;

    public FailureGroupController(
            FailureManagementService failureManagementService,
            PaginationValidator paginationValidator
    ) {
        this.failureManagementService = failureManagementService;
        this.paginationValidator = paginationValidator;
    }

    @GetMapping
    public Page<FailureGroupResponse> getFailureGroups(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @PageableDefault(
                    size = 20,
                    sort = "lastSeenAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        paginationValidator.validate(page, size);
        paginationValidator.validateSorting(
                pageable,
                paginationValidator.getFailureGroupSortFields()
        );

        return failureManagementService.getFailureGroups(
                status,
                serviceName,
                environment,
                pageable
        );
    }

    @GetMapping("/{id}")
    public FailureGroupResponse getFailureGroupById(
            @PathVariable Long id
    ) {
        paginationValidator.validateId(id);
        return failureManagementService.getFailureGroupById(id);
    }

    @GetMapping("/{id}/occurrences")
    public Page<FailureOccurrenceResponse> getOccurrences(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @PageableDefault(
                    size = 20,
                    sort = "occurredAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        paginationValidator.validateId(id);
        paginationValidator.validate(page, size);
        paginationValidator.validateSorting(
                pageable,
                paginationValidator.getOccurrenceSortFields()
        );

        return failureManagementService.getOccurrences(id, pageable);
    }

    @PatchMapping("/{id}/acknowledge")
    public FailureGroupResponse acknowledge(
            @PathVariable Long id
    ) {
        paginationValidator.validateId(id);
        return failureManagementService.acknowledge(id);
    }

    @PatchMapping("/{id}/resolve")
    public FailureGroupResponse resolve(
            @PathVariable Long id
    ) {
        paginationValidator.validateId(id);
        return failureManagementService.resolve(id);
    }

    @PatchMapping("/{id}/reopen")
    public FailureGroupResponse reopen(
            @PathVariable Long id
    ) {
        paginationValidator.validateId(id);
        return failureManagementService.reopen(id);
    }
}