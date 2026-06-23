package org.example.ticket.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspendedQueueItemDto {
    private Long ticketId;
    private String ticketNo;
    private Double score;
    private Long requestGroupId;
    private String requestGroupName;
    private Long segmentId;
    private String segmentCode;
    private String segmentName;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime skipExpireAt;
    
    private Integer rejoinCount;
}
