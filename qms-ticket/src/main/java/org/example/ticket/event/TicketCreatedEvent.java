package org.example.ticket.event;

import lombok.Getter;
import org.example.ticket.entity.Ticket;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
public class TicketCreatedEvent extends ApplicationEvent {
    private final Ticket ticket;
    private final boolean isTransfer;
    private final boolean hasTransferBonus;
    private final Integer oldTotalScore; // Used specifically for rejoin/transfer logic
    private final LocalDateTime createdAt; // Timestamp when ticket was created

    public TicketCreatedEvent(Object source, Ticket ticket) {
        super(source);
        this.ticket = ticket;
        this.isTransfer = false;
        this.hasTransferBonus = false;
        this.oldTotalScore = null;
        this.createdAt = ticket != null ? ticket.getCreatedAt() : null;
    }
    
    public TicketCreatedEvent(Object source, Ticket ticket, boolean isTransfer, Integer oldTotalScore) {
        super(source);
        this.ticket = ticket;
        this.isTransfer = isTransfer;
        this.hasTransferBonus = isTransfer; // Backward compatible (SERVING transfer had it true)
        this.oldTotalScore = oldTotalScore;
        this.createdAt = ticket != null ? ticket.getCreatedAt() : null;
    }

    public TicketCreatedEvent(Object source, Ticket ticket, boolean isTransfer, boolean hasTransferBonus, Integer oldTotalScore) {
        super(source);
        this.ticket = ticket;
        this.isTransfer = isTransfer;
        this.hasTransferBonus = hasTransferBonus;
        this.oldTotalScore = oldTotalScore;
        this.createdAt = ticket != null ? ticket.getCreatedAt() : null;
    }
}
