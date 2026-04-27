package org.example.management.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.common.dto.ApiResponse;
import org.example.management.dto.ServiceTypeDto;
import org.example.management.service.ServiceTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management/service-types")
@RequiredArgsConstructor
public class ServiceTypeController {

    private final ServiceTypeService service;

    @GetMapping
    public ApiResponse<List<ServiceTypeDto>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceTypeDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getById(id));
    }
    @GetMapping("/by-group/{requestGroupId}")
    public ApiResponse<List<ServiceTypeDto>> getByRequestGroupId(@PathVariable("requestGroupId") Long requestGroupId) {
        return ApiResponse.success(service.getByRequestGroupId(requestGroupId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServiceTypeDto> create(@Valid @RequestBody ServiceTypeDto dto) {
        return ApiResponse.success(service.create(dto), "Created service type successfully");
    }

    @PutMapping("/{id}")
    public ApiResponse<ServiceTypeDto> update(@PathVariable Long id, @Valid @RequestBody ServiceTypeDto dto) {
        return ApiResponse.success(service.update(id, dto), "Updated service type successfully");
    }
}

