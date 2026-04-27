package org.example.management.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.management.dto.BranchDto;
import org.example.management.entity.Branch;
import org.example.management.repository.BranchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository repository;

    private BranchDto mapToDto(Branch entity) {
        return BranchDto.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .address(entity.getAddress())
                .isActive(entity.getIsActive())
                .build();
    }

    public List<BranchDto> getAll() {
        return repository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public BranchDto getById(Long id) {
        Branch entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Branch not found"));
        return mapToDto(entity);
    }

    @Transactional
    public BranchDto create(BranchDto dto) {
        if (repository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Branch code already exists");
        }
        Branch entity = Branch.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .address(dto.getAddress())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
        return mapToDto(repository.save(entity));
    }

    @Transactional
    public BranchDto update(Long id, BranchDto dto) {
        Branch entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Branch not found"));

        if (!entity.getCode().equals(dto.getCode()) && repository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Branch code already exists");
        }

        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setAddress(dto.getAddress());
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        }

        return mapToDto(repository.save(entity));
    }
}

