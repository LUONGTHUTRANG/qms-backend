package org.example.management.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.common.dto.ApiResponse;
import org.example.management.dto.BranchDto;
import org.example.management.service.BranchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management/branches")
@RequiredArgsConstructor
public class BranchController {
    
    private final BranchService service;

    @GetMapping
    public ApiResponse<List<BranchDto>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<BranchDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BranchDto> create(@Valid @RequestBody BranchDto dto) {
        return ApiResponse.success(service.create(dto), "Created branch successfully");
    }

    public ApiResponse<BranchDto> update(@PathVariable Long id, @Valid @RequestBody BranchDto dto) {
        return ApiResponse.success(service.update(id, dto), "Updated branch successfully");
    }
}

