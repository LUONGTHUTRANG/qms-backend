package org.example.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ticket.entity.enums.TicketStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDto {
    private Long id;
    private Long branchId;
    private LocalDate businessDate;
    private String ticketNo;
    private Long requestGroupId;
    private Long serviceTypeId;
    private Long customerSegmentId;
    private String phoneNumber;
    private TicketStatus status;
    private Integer rejoinCount;
    private LocalDateTime skipExpireAt;
    private Integer waitCreditSeconds;
    private Integer callAttemptCount;
    private Long currentCounterId;
    private String currentCounterCode;
    private LocalDateTime lastCalledAt;
    private LocalDateTime servingAt;
    private LocalDateTime doneAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private Integer initialEwt;
    private String trackingCode;
}

