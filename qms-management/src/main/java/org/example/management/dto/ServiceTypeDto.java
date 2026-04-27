package org.example.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTypeDto {
    private Long id;

    @NotNull(message = "Request group ID is required")
    private Long requestGroupId;

    @NotBlank(message = "Code is required")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 150)
    private String name;

    private Integer averageServiceMinutes;
    private Integer priorityWeight;
    private Integer slaMinutes;

    private Boolean isActive;
}

