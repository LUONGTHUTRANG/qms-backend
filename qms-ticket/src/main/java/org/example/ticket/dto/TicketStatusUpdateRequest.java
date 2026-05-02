package org.example.ticket.dto;

import lombok.Data;
import org.example.ticket.entity.enums.TicketStatus;

@Data
public class TicketStatusUpdateRequest {
    private TicketStatus status;
    private Long reasonId;
    private String reason;
}
