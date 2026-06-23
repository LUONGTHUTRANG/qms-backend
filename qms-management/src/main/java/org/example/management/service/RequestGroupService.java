package org.example.management.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.management.dto.RequestGroupDto;
import org.example.management.dto.RequestGroupWithServicesDto;
import org.example.management.dto.ServiceTypeDto;
import org.example.management.entity.CustomerSegment;
import org.example.management.entity.RequestGroup;
import org.example.management.repository.CustomerSegmentRepository;
import org.example.management.repository.RequestGroupRepository;
import org.example.management.repository.ServiceTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequestGroupService {

    private final RequestGroupRepository repository;
    private final CustomerSegmentRepository customerSegmentRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    private RequestGroupDto mapToDto(RequestGroup entity) {
        return RequestGroupDto.builder()
                .id(entity.getId())
                .customerSegmentId(entity.getCustomerSegment() != null ? entity.getCustomerSegment().getId() : null)
                .code(entity.getCode())
                .prefixCode(entity.getPrefixCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .build();
    }

    public List<RequestGroupDto> getAll() {
        return repository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public RequestGroupDto getById(Long id) {
        RequestGroup entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Request group not found"));
        return mapToDto(entity);
    }

    private RequestGroupWithServicesDto mapToWithServicesDto(RequestGroup entity, List<ServiceTypeDto> services) {
        return RequestGroupWithServicesDto.builder()
                .id(entity.getId())
                .customerSegmentId(entity.getCustomerSegment() != null ? entity.getCustomerSegment().getId() : null)
                .code(entity.getCode())
                .prefixCode(entity.getPrefixCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .services(services)
                .build();
    }

    private ServiceTypeDto mapServiceToDto(org.example.management.entity.ServiceType entity) {
        return ServiceTypeDto.builder()
                .id(entity.getId())
                .requestGroupId(entity.getRequestGroup() != null ? entity.getRequestGroup().getId() : null)
                .code(entity.getCode())
                .name(entity.getName())
                .averageServiceMinutes(entity.getAverageServiceMinutes())
                .slaMinutes(entity.getSlaMinutes())
                .isActive(entity.getIsActive())
                .build();
    }

    public List<RequestGroupWithServicesDto> getAllWithServices() {
        return repository.findAll().stream()
                .map(entity -> {
                    List<ServiceTypeDto> services = serviceTypeRepository.findByRequestGroupId(entity.getId())
                            .stream().map(this::mapServiceToDto).collect(Collectors.toList());
                    return mapToWithServicesDto(entity, services);
                })
                .collect(Collectors.toList());
    }

    public RequestGroupWithServicesDto getByIdWithServices(Long id) {
        RequestGroup entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Request group not found"));
        List<ServiceTypeDto> services = serviceTypeRepository.findByRequestGroupId(entity.getId())
                .stream().map(this::mapServiceToDto).collect(Collectors.toList());
        return mapToWithServicesDto(entity, services);
    }

    @Transactional
    public RequestGroupDto create(RequestGroupDto dto) {
        if (repository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Code already exists");
        }
        if (repository.existsByPrefixCode(dto.getPrefixCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Prefix code already exists");
        }

        CustomerSegment customerSegment = customerSegmentRepository.findById(dto.getCustomerSegmentId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Customer segment not found"));

        RequestGroup entity = RequestGroup.builder()
                .customerSegment(customerSegment)
                .code(dto.getCode())
                .prefixCode(dto.getPrefixCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return mapToDto(repository.save(entity));
    }

    @Transactional
    public RequestGroupDto update(Long id, RequestGroupDto dto) {
        RequestGroup entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Request group not found"));

        if (!entity.getCode().equals(dto.getCode()) && repository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Code already exists");
        }
        if (!entity.getPrefixCode().equals(dto.getPrefixCode()) && repository.existsByPrefixCode(dto.getPrefixCode())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Prefix code already exists");
        }

        CustomerSegment customerSegment = customerSegmentRepository.findById(dto.getCustomerSegmentId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Customer segment not found"));

        entity.setCustomerSegment(customerSegment);
        entity.setCode(dto.getCode());
        entity.setPrefixCode(dto.getPrefixCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        }

        return mapToDto(repository.save(entity));
    }
}
