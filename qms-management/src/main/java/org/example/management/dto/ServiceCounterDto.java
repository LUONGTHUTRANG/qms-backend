package org.example.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.management.entity.enums.CounterStatus;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCounterDto {
    private Long id;

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotBlank(message = "Code is required")
    @Size(max = 30)
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 150)
    private String name;

    private CounterStatus status;
    private Boolean isActive;

    private Set<Long> requestGroupIds;
    private Set<Long> customerSegmentIds;
}

