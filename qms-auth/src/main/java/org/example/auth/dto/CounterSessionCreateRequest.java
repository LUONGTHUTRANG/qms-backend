package org.example.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CounterSessionCreateRequest {
    @NotNull(message = "Counter ID is required")
    private Long counterId;
}

