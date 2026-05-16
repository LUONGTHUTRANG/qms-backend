package org.example.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ticket.entity.enums.TicketStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketListRequest {
    private TicketStatus status;
    private Long counterId; // null = không lọc, != null = filter theo current_counter_id
}

