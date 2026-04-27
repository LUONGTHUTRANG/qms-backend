package org.example.ticket.client.dto;

import lombok.Data;

@Data
public class CounterSessionDto {
    private Long id;
    private Long userId;
    private Long counterId;
    private Long branchId;
    private String status;
}
