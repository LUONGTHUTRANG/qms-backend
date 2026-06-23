package org.example.ticket.event;

import lombok.Getter;
import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.enums.TicketStatus;
import org.springframework.context.ApplicationEvent;

@Getter
public class TicketRemovedFromQueueEvent extends ApplicationEvent {
    private final Ticket ticket;
    private final TicketStatus newStatus;
    private final Long branchId;
    private final Long requestGroupId;
    private final Long segmentId;

    public TicketRemovedFromQueueEvent(Object source, Ticket ticket, TicketStatus newStatus) {
        this(source, ticket, newStatus, ticket.getBranchId(), ticket.getRequestGroupId(), ticket.getCustomerSegmentId());
    }

    public TicketRemovedFromQueueEvent(Object source, Ticket ticket, TicketStatus newStatus,
                                       Long branchId, Long requestGroupId, Long segmentId) {
        super(source);
        this.ticket = ticket;
        this.newStatus = newStatus;
        this.branchId = branchId;
        this.requestGroupId = requestGroupId;
        this.segmentId = segmentId;
    }
}
