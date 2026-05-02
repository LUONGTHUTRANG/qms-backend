package org.example.management.service;

import lombok.RequiredArgsConstructor;
import org.example.common.exception.BusinessException;
import org.example.management.dto.ReasonDto;
import org.example.management.entity.Reason;
import org.example.management.entity.enums.ReasonType;
import org.example.management.repository.ReasonRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReasonService {

    private final ReasonRepository reasonRepository;

    private ReasonDto mapToDto(Reason entity) {
        return ReasonDto.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .type(entity.getType())
                .isActive(entity.getIsActive())
                .build();
    }

    public List<ReasonDto> getAllActive() {
        return reasonRepository.findByIsActiveTrue().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<ReasonDto> getByType(ReasonType type) {
        return reasonRepository.findByTypeAndIsActiveTrue(type).stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public ReasonDto getById(Long id) {
        return reasonRepository.findById(id).map(this::mapToDto)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Reason not found"));
    }

    @Transactional
    public ReasonDto create(ReasonDto dto) {
        if (reasonRepository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Reason code already exists");
        }
        Reason reason = Reason.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .type(dto.getType())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
        return mapToDto(reasonRepository.save(reason));
    }

    @Transactional
    public ReasonDto update(Long id, ReasonDto dto) {
        Reason reason = reasonRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Reason not found"));
        if (!reason.getCode().equals(dto.getCode()) && reasonRepository.existsByCode(dto.getCode())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Reason code already exists");
        }
        reason.setCode(dto.getCode());
        reason.setName(dto.getName());
        reason.setType(dto.getType());
        if (dto.getIsActive() != null) {
            reason.setIsActive(dto.getIsActive());
        }
        return mapToDto(reasonRepository.save(reason));
    }

    @Transactional
    public void delete(Long id) {
        Reason reason = reasonRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Reason not found"));
        reason.setIsActive(false);
        reasonRepository.save(reason);
    }
}
