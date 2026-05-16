package org.example.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.management.entity.enums.CounterStatus;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCounterWithTicketDto {
    private Long id;
    private Long branchId;
    private String code;
    private String name;
    private CounterStatus status;
    private Boolean isActive;
    private Set<Long> requestGroupIds;
    private Set<Long> customerSegmentIds;

    // Vé hiện tại đang được phục vụ (nếu có)
    private Integer currentTicketId;
    private String currentTicketNo;
    private String currentTicketStatus;
}



