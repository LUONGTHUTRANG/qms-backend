package org.example.ticket.dto;

import lombok.Data;

@Data
public class TicketTransferRequest {
    private Long newRequestGroupId;
    private Long newServiceTypeId;
    private Long reasonId;
    private String reason; // Keep as fallback if frontend still sends it
}
