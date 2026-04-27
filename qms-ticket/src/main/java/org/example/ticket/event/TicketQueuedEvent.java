package org.example.ticket.event;

import lombok.Getter;
import org.example.ticket.entity.Ticket;
import org.springframework.context.ApplicationEvent;

@Getter
public class TicketQueuedEvent extends ApplicationEvent {
    private final Ticket ticket;
    private final Double score;

    public TicketQueuedEvent(Object source, Ticket ticket, Double score) {
        super(source);
        this.ticket = ticket;
        this.score = score;
    }
}

