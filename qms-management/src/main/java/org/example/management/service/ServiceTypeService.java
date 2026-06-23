package org.example.management.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.management.dto.ServiceTypeDto;
import org.example.management.entity.RequestGroup;
import org.example.management.entity.ServiceType;
import org.example.management.repository.RequestGroupRepository;
import org.example.management.repository.ServiceTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceTypeService {

    private final ServiceTypeRepository repository;
    private final RequestGroupRepository requestGroupRepository;

    private ServiceTypeDto mapToDto(ServiceType entity) {
        return ServiceTypeDto.builder()
                .id(entity.getId())
                .requestGroupId(entity.getRequestGroup().getId())
                .code(entity.getCode())
                .name(entity.getName())
                .averageServiceMinutes(entity.getAverageServiceMinutes())
                .slaMinutes(entity.getSlaMinutes())
                .isActive(entity.getIsActive())
                .build();
    }

    public List<ServiceTypeDto> getAll() {
        return repository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ServiceTypeDto getById(Long id) {
        ServiceType entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Service type not found"));
        return mapToDto(entity);
    }

    public List<ServiceTypeDto> getByRequestGroupId(Long requestGroupId) {
        return repository.findByRequestGroupId(requestGroupId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ServiceTypeDto create(ServiceTypeDto dto) {
        if (repository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Code already exists");
        }

        RequestGroup requestGroup = requestGroupRepository.findById(dto.getRequestGroupId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Request group not found"));

        ServiceType entity = ServiceType.builder()
                .requestGroup(requestGroup)
                .code(dto.getCode())
                .name(dto.getName())
                .averageServiceMinutes(dto.getAverageServiceMinutes() != null ? dto.getAverageServiceMinutes() : 10)
                .slaMinutes(dto.getSlaMinutes() != null ? dto.getSlaMinutes() : 15)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return mapToDto(repository.save(entity));
    }

    @Transactional
    public ServiceTypeDto update(Long id, ServiceTypeDto dto) {
        ServiceType entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Service type not found"));

        if (!entity.getCode().equals(dto.getCode()) && repository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Code already exists");
        }

        if (!entity.getRequestGroup().getId().equals(dto.getRequestGroupId())) {
            RequestGroup requestGroup = requestGroupRepository.findById(dto.getRequestGroupId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Request group not found"));
            entity.setRequestGroup(requestGroup);
        }

        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        
        if(dto.getAverageServiceMinutes() != null) entity.setAverageServiceMinutes(dto.getAverageServiceMinutes());
        if(dto.getSlaMinutes() != null) entity.setSlaMinutes(dto.getSlaMinutes());
        if(dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());

        return mapToDto(repository.save(entity));
    }
}

