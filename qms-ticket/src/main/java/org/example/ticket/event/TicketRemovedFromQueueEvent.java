package org.example.ticket.event;

import lombok.Getter;
import org.example.ticket.entity.Ticket;
import org.example.ticket.entity.enums.TicketStatus;
import org.springframework.context.ApplicationEvent;

@Getter
public class TicketRemovedFromQueueEvent extends ApplicationEvent {
    private final Ticket ticket;
    private final TicketStatus newStatus;

    public TicketRemovedFromQueueEvent(Object source, Ticket ticket, TicketStatus newStatus) {
        super(source);
        this.ticket = ticket;
        this.newStatus = newStatus;
    }
}
