package org.example.management.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.management.dto.CustomerSegmentDto;
import org.example.management.entity.CustomerSegment;
import org.example.management.repository.CustomerSegmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerSegmentService {

    private final CustomerSegmentRepository repository;

    private CustomerSegmentDto mapToDto(CustomerSegment entity) {
        return CustomerSegmentDto.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .targetWaitMinutes(entity.getTargetWaitMinutes())
                .isActive(entity.getIsActive())
                .build();
    }

    public List<CustomerSegmentDto> getAll() {
        return repository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public CustomerSegmentDto getById(Long id) {
        CustomerSegment entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Customer segment not found for id: " + id));
        return mapToDto(entity);
    }

    @Transactional
    public CustomerSegmentDto create(CustomerSegmentDto dto) {
        if (repository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Customer Segment code already exists: " + dto.getCode());
        }

        CustomerSegment entity = CustomerSegment.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .targetWaitMinutes(dto.getTargetWaitMinutes())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return mapToDto(repository.save(entity));
    }

    @Transactional
    public CustomerSegmentDto update(Long id, CustomerSegmentDto dto) {
        CustomerSegment entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Customer segment not found for id: " + id));

        if (!entity.getCode().equals(dto.getCode()) && repository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Customer Segment code already exists: " + dto.getCode());
        }

        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setTargetWaitMinutes(dto.getTargetWaitMinutes());
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        }

        return mapToDto(repository.save(entity));
    }
}

