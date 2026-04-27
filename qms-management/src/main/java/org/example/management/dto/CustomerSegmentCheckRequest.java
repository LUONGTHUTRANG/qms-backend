package org.example.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerSegmentCheckRequest {
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotNull(message = "Customer segment ID is required")
    private Long customerSegmentId;
}

