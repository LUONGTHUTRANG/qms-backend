package org.example.ticket.client.dto;

import lombok.Data;

@Data
public class ServiceTypeConfigDto {
    private Long id;
    private String code;
    private String name;
    private Integer averageServiceMinutes;
    private Integer priorityWeight;
    private Integer slaMinutes;
}

