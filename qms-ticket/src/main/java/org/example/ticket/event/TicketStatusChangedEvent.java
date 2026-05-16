package org.example.ticket.event;

import lombok.Getter;
import org.example.ticket.dto.SuspendedQueueItemDto;
import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.enums.TicketStatus;
import org.springframework.context.ApplicationEvent;

@Getter
public class TicketStatusChangedEvent extends ApplicationEvent {
    private final Ticket ticket;
    private final TicketStatus oldStatus;
    private final SuspendedQueueItemDto queueItemData;

    public TicketStatusChangedEvent(Object source, Ticket ticket, TicketStatus oldStatus) {
        super(source);
        this.ticket = ticket;
        this.oldStatus = oldStatus;
        this.queueItemData = null;
    }

    public TicketStatusChangedEvent(Object source, Ticket ticket, TicketStatus oldStatus, 
                                    SuspendedQueueItemDto queueItemData) {
        super(source);
        this.ticket = ticket;
        this.oldStatus = oldStatus;
        this.queueItemData = queueItemData;
    }
}

