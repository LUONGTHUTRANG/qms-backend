package org.example.management.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.dto.ApiResponse;
import org.example.management.dto.ReasonDto;
import org.example.management.entity.enums.ReasonType;
import org.example.management.service.ReasonService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management/reasons")
@RequiredArgsConstructor
public class ReasonController {

    private final ReasonService reasonService;

    @GetMapping
    public ApiResponse<List<ReasonDto>> getAll(@RequestParam(name = "type", required = false) ReasonType type) {
        if (type != null) {
            return ApiResponse.success(reasonService.getByType(type));
        }
        return ApiResponse.success(reasonService.getAllActive());
    }

    @GetMapping("/{id}")
    public ApiResponse<ReasonDto> getById(@PathVariable Long id) {
        return ApiResponse.success(reasonService.getById(id));
    }

    @PostMapping
    public ApiResponse<ReasonDto> create(@RequestBody ReasonDto dto) {
        return ApiResponse.success(reasonService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReasonDto> update(@PathVariable Long id, @RequestBody ReasonDto dto) {
        return ApiResponse.success(reasonService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        reasonService.delete(id);
        return ApiResponse.success(null);
    }
}
