package org.example.ticket.dto;

import lombok.Builder;
import lombok.Data;
import org.example.ticket.entity.enums.TicketStatus;

@Data
@Builder
public class TicketTrackingDto  {
    private String ticketNo;
    private TicketStatus status;
    private String currentCounterCode;
    private int peopleAhead;
    private int activeCounters;
    private int estimatedWaitTime;
}
