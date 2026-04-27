package org.example.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestGroupDto {
    private Long id;

    @NotBlank(message = "Code is required")
    @Size(max = 50)
    private String code;

    private Long customerSegmentId;

    @NotBlank(message = "Prefix code is required")
    @Size(max = 5)
    private String prefixCode;

    @NotBlank(message = "Name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 255)
    private String description;

    private Boolean isActive;
}


