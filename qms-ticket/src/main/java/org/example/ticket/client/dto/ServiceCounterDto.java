package org.example.ticket.client.dto;

import lombok.Data;
import java.util.Set;

@Data
public class ServiceCounterDto {
    private Long id;
    private Long branchId;
    private String code;
    private String name;
    private Set<Long> requestGroupIds;
    private Set<Long> customerSegmentIds;
}
