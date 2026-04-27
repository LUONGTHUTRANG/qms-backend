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
public class BranchDto {
    private Long id;

    @NotBlank(message = "Code is required")
    @Size(max = 30)
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "Address is required")
    @Size(max = 255)
    private String address;

    private Boolean isActive;
}

