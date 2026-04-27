package org.example.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueItemDto {
    private Long ticketId;
    private String ticketNo;
    private Double score;
    private Long requestGroupId;
    private String requestGroupName;
    private Long segmentId;
    private String segmentCode;
    private String segmentName;
}
