package org.example.ticket.event;

import lombok.Getter;
import org.example.ticket.entity.Ticket;
import org.springframework.context.ApplicationEvent;

@Getter
public class TicketCreatedEvent extends ApplicationEvent {
    private final Ticket ticket;
    private final boolean isTransfer;
    private final Integer oldTotalScore; // Used specifically for rejoin/transfer logic

    public TicketCreatedEvent(Object source, Ticket ticket) {
        super(source);
        this.ticket = ticket;
        this.isTransfer = false;
        this.oldTotalScore = null;
    }
    
    public TicketCreatedEvent(Object source, Ticket ticket, boolean isTransfer, Integer oldTotalScore) {
        super(source);
        this.ticket = ticket;
        this.isTransfer = isTransfer;
        this.oldTotalScore = oldTotalScore;
    }
}

