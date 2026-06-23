package org.example.ticket.client.dto;

import lombok.Data;

@Data
public class ReasonConfigDto {
    private Long id;
    private String code;
    private String name;
    private String type;
    private Boolean isActive;
}
