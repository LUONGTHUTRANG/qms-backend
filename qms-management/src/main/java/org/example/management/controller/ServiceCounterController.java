package org.example.management.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.common.dto.ApiResponse;
import org.example.management.dto.ServiceCounterDto;
import org.example.management.dto.ServiceCounterWithTicketDto;
import org.example.management.service.ServiceCounterService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management/service-counters")
@RequiredArgsConstructor
public class ServiceCounterController {

    private final ServiceCounterService service;

    @GetMapping
    public ApiResponse<List<ServiceCounterDto>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @GetMapping("/by-branch/{branchId}")
    public ApiResponse<List<ServiceCounterDto>> getByBranchId(@PathVariable("branchId") Long branchId) {
        return ApiResponse.success(service.getByBranchId(branchId));
    }

    @GetMapping("/by-branch/{branchId}/with-tickets")
    public ApiResponse<List<ServiceCounterWithTicketDto>> getCountersByBranchWithTickets(
            @PathVariable("branchId") Long branchId) {
        return ApiResponse.success(service.getCountersByBranchWithTickets(branchId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceCounterDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServiceCounterDto> create(@Valid @RequestBody ServiceCounterDto dto) {
        return ApiResponse.success(service.create(dto), "Created service counter successfully");
    }

    @PutMapping("/{id}")
    public ApiResponse<ServiceCounterDto> update(@PathVariable("id") Long id, @Valid @RequestBody ServiceCounterDto dto) {
        return ApiResponse.success(service.update(id, dto), "Updated service counter successfully");
    }

    @GetMapping("/{counterId}/topics")
    public ApiResponse<List<String>> getCounterSubscriptionTopics(
            @PathVariable("counterId") Long counterId) {
        return ApiResponse.success(service.getSubscriptionTopicsByCounterId(counterId), "Subscription topics retrieved successfully");
    }
}
