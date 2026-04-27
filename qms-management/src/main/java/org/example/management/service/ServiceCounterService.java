package org.example.management.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.management.dto.ServiceCounterDto;
import org.example.management.entity.Branch;
import org.example.management.entity.CustomerSegment;
import org.example.management.entity.RequestGroup;
import org.example.management.entity.ServiceCounter;
import org.example.management.entity.enums.CounterStatus;
import org.example.management.repository.BranchRepository;
import org.example.management.repository.CustomerSegmentRepository;
import org.example.management.repository.RequestGroupRepository;
import org.example.management.repository.ServiceCounterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceCounterService {

    private final ServiceCounterRepository repository;
    private final BranchRepository branchRepository;
    private final RequestGroupRepository requestGroupRepository;
    private final CustomerSegmentRepository customerSegmentRepository;

    private ServiceCounterDto mapToDto(ServiceCounter entity) {
        return ServiceCounterDto.builder()
                .id(entity.getId())
                .branchId(entity.getBranch().getId())
                .code(entity.getCode())
                .name(entity.getName())
                .status(entity.getStatus())
                .isActive(entity.getIsActive())
                .requestGroupIds(entity.getRequestGroups() != null ? entity.getRequestGroups().stream().map(RequestGroup::getId).collect(Collectors.toSet()) : new HashSet<>())
                .customerSegmentIds(entity.getCustomerSegments() != null ? entity.getCustomerSegments().stream().map(CustomerSegment::getId).collect(Collectors.toSet()) : new HashSet<>())
                .build();
    }

    public List<ServiceCounterDto> getAll() {
        return repository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<ServiceCounterDto> getByBranchId(Long branchId) {
        return repository.findByBranchId(branchId).stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public ServiceCounterDto getById(Long id) {
        ServiceCounter entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Service counter not found"));
        return mapToDto(entity);
    }

    @Transactional
    public ServiceCounterDto create(ServiceCounterDto dto) {
        if (repository.existsByBranchIdAndCode(dto.getBranchId(), dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Counter code already exists in this branch");
        }

        Branch branch = branchRepository.findById(dto.getBranchId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Branch not found"));

        Set<RequestGroup> requestGroups = dto.getRequestGroupIds() != null ?
                new HashSet<>(requestGroupRepository.findAllById(dto.getRequestGroupIds())) : new HashSet<>();

        Set<CustomerSegment> customerSegments = dto.getCustomerSegmentIds() != null ?
                new HashSet<>(customerSegmentRepository.findAllById(dto.getCustomerSegmentIds())) : new HashSet<>();

        ServiceCounter entity = ServiceCounter.builder()
                .branch(branch)
                .code(dto.getCode())
                .name(dto.getName())
                .status(dto.getStatus() != null ? dto.getStatus() : CounterStatus.AVAILABLE)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .requestGroups(requestGroups)
                .customerSegments(customerSegments)
                .build();

        return mapToDto(repository.save(entity));
    }

    @Transactional
    public ServiceCounterDto update(Long id, ServiceCounterDto dto) {
        ServiceCounter entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Service counter not found"));

        if (!entity.getCode().equals(dto.getCode()) && repository.existsByBranchIdAndCode(dto.getBranchId(), dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Counter code already exists in this branch");
        }

        if (!entity.getBranch().getId().equals(dto.getBranchId())) {
            Branch branch = branchRepository.findById(dto.getBranchId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Branch not found"));
            entity.setBranch(branch);
        }

        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());

        if (dto.getRequestGroupIds() != null) {
            entity.setRequestGroups(new HashSet<>(requestGroupRepository.findAllById(dto.getRequestGroupIds())));
        }

        if (dto.getCustomerSegmentIds() != null) {
            entity.setCustomerSegments(new HashSet<>(customerSegmentRepository.findAllById(dto.getCustomerSegmentIds())));
        }

        return mapToDto(repository.save(entity));
    }
}

