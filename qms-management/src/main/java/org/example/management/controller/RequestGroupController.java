package org.example.management.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.common.dto.ApiResponse;
import org.example.management.dto.RequestGroupDto;
import org.example.management.service.RequestGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management/request-groups")
@RequiredArgsConstructor
public class RequestGroupController {

    private final RequestGroupService service;

    @GetMapping
    public ApiResponse<List<org.example.management.dto.RequestGroupWithServicesDto>> getAll() {
        return ApiResponse.success(service.getAllWithServices());
    }

    @GetMapping("/{id}")
    public ApiResponse<org.example.management.dto.RequestGroupWithServicesDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getByIdWithServices(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RequestGroupDto> create(@Valid @RequestBody RequestGroupDto dto) {
        return ApiResponse.success(service.create(dto), "Created request group successfully");
    }

    @PutMapping("/{id}")
    public ApiResponse<RequestGroupDto> update(@PathVariable("id") Long id, @Valid @RequestBody RequestGroupDto dto) {
        return ApiResponse.success(service.update(id, dto), "Updated request group successfully");
    }
}
