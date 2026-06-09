package org.example.ticket.client.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerSegmentConfigDto implements Serializable {
    private Long id;
    private String code;
    private String name;
    private Integer basePriorityScore;
}

