package org.example.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestGroupWithServicesDto {
    private Long id;
    private Long customerSegmentId;
    private String code;
    private String prefixCode;
    private String name;
    private String description;
    private Boolean isActive;
    private List<ServiceTypeDto> services;
}

