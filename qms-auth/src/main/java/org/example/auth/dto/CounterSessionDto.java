package org.example.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.auth.entity.enums.CounterSessionStatus;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterSessionDto {
    private Long id;
    private Long userId;
    private Long counterId;
    private Long branchId;
    private CounterSessionStatus status;
    private String fullName; // Additional contextual info
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}

