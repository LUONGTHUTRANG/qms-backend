package org.example.ticket.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestGroupDto {
    private Long id;
    private Long customerSegmentId;
    private String code;
    private String prefixCode;
    private String name;
    private Integer defaultServingTime;
}
