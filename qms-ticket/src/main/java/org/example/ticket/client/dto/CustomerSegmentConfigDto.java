package org.example.ticket.client.dto;

import lombok.Data;

@Data
public class CustomerSegmentConfigDto {
    private Long id;
    private String code;
    private String name;
    private Integer basePriorityScore;
}

