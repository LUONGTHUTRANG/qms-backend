package org.example.management.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.common.dto.ApiResponse;
import org.example.management.dto.CustomerSegmentDto;
import org.example.management.service.CustomerSegmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management/customer-segments")
@RequiredArgsConstructor
public class CustomerSegmentController {

    private final CustomerSegmentService service;

    @GetMapping
    public ApiResponse<List<CustomerSegmentDto>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerSegmentDto> getById(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerSegmentDto> create(@Valid @RequestBody CustomerSegmentDto dto) {
        return ApiResponse.success(service.create(dto), "Created customer segment successfully");
    }

    public ApiResponse<CustomerSegmentDto> update(@PathVariable Long id, @Valid @RequestBody CustomerSegmentDto dto) {
        return ApiResponse.success(service.update(id, dto), "Updated customer segment successfully");
    }
}

